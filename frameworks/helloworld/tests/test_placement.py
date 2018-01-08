import json
import logging

import pytest
import sdk_api
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_utils
import shakedown
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


def _escape_placement_for_1_9(options: dict) -> dict:
    # 1.9 requires `\"` to be escped to `\\\"`
    # when submitting placement constraints
    log.info(options)
    if not sdk_utils.dcos_version_less_than("1.10"):
        log.info("DC/OS version > 1.10")
        return options

    def escape_section_placement(section: str, options: dict) -> dict:
        if section in options and "placement" in options[section]:
            options[section]["placement"] = options[section]["placement"].replace("\"", "\\\"")
            log.info("Escaping %s", section)

        log.info(options)
        return options

    return escape_section_placement("hello", escape_section_placement("world", options))


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_region_zone_injection():
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 3)
    assert fault_domain_vars_are_present('hello-0')
    assert fault_domain_vars_are_present('world-0')
    assert fault_domain_vars_are_present('world-1')
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


def fault_domain_vars_are_present(pod_instance):
    info = sdk_api.get(config.SERVICE_NAME, '/v1/pod/{}/info'.format(pod_instance)).json()[0]['info']
    variables = info['command']['environment']['variables']
    region = next((var for var in variables if var['name'] == 'REGION'), ['NO_REGION'])
    zone = next((var for var in variables if var['name'] == 'ZONE'), ['NO_ZONE'])

    return region != 'NO_REGION' and zone != 'NO_ZONE' and len(region) > 0 and len(zone) > 0


@pytest.mark.dcos_min_version('1.9')
@pytest.mark.smoke
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_rack_not_found():
    options = _escape_placement_for_1_9({
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "placement": "[[\"rack_id\", \"LIKE\", \"rack-foo-.*\"]]"
        },
        "world": {
            "placement": "[[\"rack_id\", \"LIKE\", \"rack-foo-.*\"]]"
        }
    })

    # scheduler should fail to deploy, don't wait for it to complete:
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 0,
        additional_options=options, wait_for_deployment=False)
    try:
        sdk_tasks.check_running(config.SERVICE_NAME, 1, timeout_seconds=60)
        assert False, "Should have failed to deploy anything"
    except AssertionError as arg:
        raise arg
    except:
        pass # expected to fail

    pl = sdk_plan.get_deployment_plan(config.SERVICE_NAME)

    # check that everything is still stuck looking for a match:
    assert pl['status'] == 'IN_PROGRESS'

    assert len(pl['phases']) == 2

    phase1 = pl['phases'][0]
    assert phase1['status'] == 'IN_PROGRESS'
    steps1 = phase1['steps']
    assert len(steps1) == 1
    assert steps1[0]['status'] in ('PREPARED', 'PENDING') # first step may be PREPARED

    phase2 = pl['phases'][1]
    assert phase2['status'] == 'PENDING'
    steps2 = phase2['steps']
    assert len(steps2) == 2
    assert steps2[0]['status'] == 'PENDING'
    assert steps2[1]['status'] == 'PENDING'
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_unique_zone_fails():
    options = _escape_placement_for_1_9({
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "placement": "[[\"@zone\", \"UNIQUE\"]]"
        },
        "world": {
            "placement": "[[\"@zone\", \"UNIQUE\"]]"
        }
    })

    fail_placement(options)


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_max_per_zone_fails():
    options = _escape_placement_for_1_9({
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "placement": "[[\"@zone\", \"MAX_PER\", \"1\"]]"
        },
        "world": {
            "placement": "[[\"@zone\", \"MAX_PER\", \"1\"]]"
        }
    })

    fail_placement(options)


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_max_per_zone_succeeds():
    options = _escape_placement_for_1_9({
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "placement": "[[\"@zone\", \"MAX_PER\", \"1\"]]"
        },
        "world": {
            "placement": "[[\"@zone\", \"MAX_PER\", \"2\"]]"
        }
    })

    succeed_placement(options)


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_group_by_zone_succeeds():
    options = _escape_placement_for_1_9({
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "placement": "[[\"@zone\", \"GROUP_BY\", \"1\"]]"
        },
        "world": {
            "placement": "[[\"@zone\", \"GROUP_BY\", \"1\"]]"
        }
    })
    succeed_placement(options)


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_group_by_zone_fails():
    options = _escape_placement_for_1_9({
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "placement": "[[\"@zone\", \"GROUP_BY\", \"1\"]]"
        },
        "world": {
            "placement": "[[\"@zone\", \"GROUP_BY\", \"2\"]]"
        }
    })

    fail_placement(options)


def succeed_placement(options):
    """
    This assumes that the DC/OS cluster is reporting that all agents are in a single zone.
    """
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 0, additional_options=options)
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


def fail_placement(options):
    """
    This assumes that the DC/OS cluster is reporting that all agents are in a single zone.
    """

    # scheduler should fail to deploy, don't wait for it to complete:
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 0,
                        additional_options=options, wait_for_deployment=False)
    sdk_plan.wait_for_step_status(config.SERVICE_NAME, 'deploy', 'world', 'world-0:[server]', 'COMPLETE')

    pl = sdk_plan.get_deployment_plan(config.SERVICE_NAME)

    # check that everything is still stuck looking for a match:
    assert pl['status'] == 'IN_PROGRESS'

    assert len(pl['phases']) == 2

    phase1 = pl['phases'][0]
    assert phase1['status'] == 'COMPLETE'
    steps1 = phase1['steps']
    assert len(steps1) == 1

    phase2 = pl['phases'][1]
    assert phase2['status'] == 'IN_PROGRESS'
    steps2 = phase2['steps']
    assert len(steps2) == 2
    assert steps2[0]['status'] == 'COMPLETE'
    assert steps2[1]['status'] in ('PREPARED', 'PENDING')

    try:
        sdk_tasks.check_running(config.SERVICE_NAME, 3, timeout_seconds=30)
        assert False, "Should have failed to deploy world-1"
    except AssertionError as arg:
        raise arg
    except:
        pass # expected to fail

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_hostname_unique():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    options = _escape_placement_for_1_9({
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": config.get_num_private_agents(),
            "placement": "[[\"hostname\", \"UNIQUE\"]]"
        },
        "world": {
            "count": config.get_num_private_agents(),
            "placement": "[[\"hostname\", \"UNIQUE\"]]"
        }
    })

    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME,
        config.get_num_private_agents() * 2, additional_options=options)
    # hello deploys first. One "world" task should end up placed with each "hello" task.
    # ensure "hello" task can still be placed with "world" task
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace hello-0')
    sdk_plan.wait_for_kicked_off_recovery(config.SERVICE_NAME)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)
    sdk_tasks.check_running(config.SERVICE_NAME, config.get_num_private_agents() * 2 - 1, timeout_seconds=10)
    sdk_tasks.check_running(config.SERVICE_NAME, config.get_num_private_agents() * 2)
    ensure_count_per_agent(hello_count=1, world_count=1)


@pytest.mark.sanity
def test_max_per_hostname():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    options = _escape_placement_for_1_9({
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": config.get_num_private_agents() * 2,
            "placement": "[[\"hostname\", \"MAX_PER\", \"2\"]]"
        },
        "world": {
            "count": config.get_num_private_agents() * 3,
            "placement": "[[\"hostname\", \"MAX_PER\", \"3\"]]"
        }
    })

    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME,
        config.get_num_private_agents() * 5, additional_options=options)
    ensure_max_count_per_agent(hello_count=2, world_count=3)


@pytest.mark.sanity
def test_rr_by_hostname():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    options = _escape_placement_for_1_9({
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": config.get_num_private_agents() * 2,
            "placement": "[[\"hostname\", \"GROUP_BY\", \"{}\"]]".format(config.get_num_private_agents())
        },
        "world": {
            "count": config.get_num_private_agents() * 2,
            "placement": "[[\"hostname\", \"GROUP_BY\", \"{}\"]]".format(config.get_num_private_agents())
        }
    })

    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME,
        config.get_num_private_agents() * 4, additional_options=options)
    ensure_max_count_per_agent(hello_count=2, world_count=2)


@pytest.mark.sanity
def test_cluster():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    some_agent = shakedown.get_private_agents().pop()
    options = _escape_placement_for_1_9({
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": config.get_num_private_agents(),
            "placement": "[[\"hostname\", \"CLUSTER\", \"{}\"]]".format(some_agent)
        },
        "world": {
            "count": 0
        }
    })

    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME,
        config.get_num_private_agents(), additional_options=options)
    ensure_count_per_agent(hello_count=config.get_num_private_agents(), world_count=0)


def get_hello_world_agent_sets():
    all_tasks = shakedown.get_service_tasks(config.SERVICE_NAME)
    hello_agents = []
    world_agents = []
    for task in all_tasks:
        if task['name'].startswith('hello-'):
            hello_agents.append(task['slave_id'])
        elif task['name'].startswith('world-'):
            world_agents.append(task['slave_id'])
        else:
            assert False, "Unknown task: " + task['name']
    return hello_agents, world_agents


def ensure_count_per_agent(hello_count, world_count):
    hello_agents, world_agents = get_hello_world_agent_sets()
    assert len(hello_agents) == len(set(hello_agents)) * hello_count
    assert len(world_agents) == len(set(world_agents)) * world_count


def groupby_count(a):
    h = {}
    for i in a:
        if i not in h:
            h[i] = 0
        else:
            h[i] += 1
    return h


def assert_max_count(counts, max_count):
    assert not any(counts[i] > max_count for i in counts)


def ensure_max_count_per_agent(hello_count, world_count):
    hello_agents, world_agents = get_hello_world_agent_sets()
    hello_agent_counts = groupby_count(hello_agents)
    world_agent_counts = groupby_count(world_agents)
    assert_max_count(hello_agent_counts, hello_count)
    assert_max_count(world_agent_counts, world_count)

@pytest.mark.sanity
def test_updated_placement_constraints_not_applied_with_other_changes():
    some_agent, other_agent, old_ids = setup_constraint_switch()

    # Additionally, modify the task count to be higher.
    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    marathon_config['env']['HELLO_COUNT'] = '2'
    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)

    # Now, an additional hello-server task will launch
    # where the _new_ constraint will tell it to be.
    sdk_tasks.check_running(config.SERVICE_NAME, 2)
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    assert get_task_host('hello-0-server') == some_agent
    assert get_task_host('hello-1-server') == other_agent


@pytest.mark.sanity
def test_updated_placement_constraints_no_task_change():
    some_agent, other_agent, old_ids = setup_constraint_switch()

    sdk_tasks.check_tasks_not_updated(config.SERVICE_NAME, 'hello', old_ids)

    assert get_task_host('hello-0-server') == some_agent


@pytest.mark.sanity
def test_updated_placement_constraints_restarted_tasks_dont_move():
    some_agent, other_agent, old_ids = setup_constraint_switch()

    # Restart the task, and verify it doesn't move hosts
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod restart hello-0')
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'hello', old_ids)

    assert get_task_host('hello-0-server') == some_agent


@pytest.mark.sanity
def test_updated_placement_constraints_replaced_tasks_do_move():
    some_agent, other_agent, old_ids = setup_constraint_switch()

    # Replace the task, and verify it moves hosts
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace hello-0')
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'hello', old_ids)

    assert get_task_host('hello-0-server') == other_agent


def setup_constraint_switch():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    agents = shakedown.get_private_agents()
    some_agent = agents[0]
    other_agent = agents[1]
    log.info('Agents: %s %s', some_agent, other_agent)
    assert some_agent != other_agent
    options = _escape_placement_for_1_9({
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": 1,
            # First, we stick the pod to some_agent
            "placement": "[[\"hostname\", \"LIKE\", \"{}\"]]".format(some_agent)
        },
        "world": {
            "count": 0
        }
    })
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 1, additional_options=options)
    sdk_tasks.check_running(config.SERVICE_NAME, 1)
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello')

    # Now, stick it to other_agent
    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    marathon_config['env']['HELLO_PLACEMENT'] = "[[\"hostname\", \"LIKE\", \"{}\"]]".format(other_agent)
    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)
    # Wait for the scheduler to be up and settled before advancing.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    return some_agent, other_agent, hello_ids


def get_task_host(task_name):
    out = sdk_cmd.run_cli('task {} --json'.format(task_name))

    for label in json.loads(out)[0]['labels']:
        if label['key'] == 'offer_hostname':
            return label['value']

    raise Exception("offer_hostname label is not present!")
