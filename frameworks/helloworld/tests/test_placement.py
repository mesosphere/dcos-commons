import functools
import json
import logging

import pytest
import sdk_agents
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_utils
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version("1.12")
@pytest.mark.sanity
def test_scheduler_task_placement_by_marathon():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        # This test ensures that the placement of the scheduler task itself works as expected.
        some_private_agent = sdk_agents.get_private_agents().pop()["hostname"]
        logging.info("Constraining scheduler placement to [{}]".format(some_private_agent))
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            expected_running_tasks=1,
            additional_options={
                "service": {
                    "constraints": [["hostname", "CLUSTER", "{}".format(some_private_agent)]],
                    "yaml": "simple",
                }
            },
            wait_for_deployment=False,
        )
        summary = sdk_tasks.get_service_tasks("marathon", config.SERVICE_NAME)
        assert len(summary) == 1, "More than 1 task matched name [{}] : [{}]".format(
            config.SERVICE_NAME, summary
        )
        assert (
            some_private_agent == summary.pop().host
        ), "Scheduler task constraint placement failed by marathon"
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version("1.11")
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_region_zone_injection():
    def fault_domain_vars_are_present(pod_instance):
        info = sdk_cmd.service_request(
            "GET", config.SERVICE_NAME, "/v1/pod/{}/info".format(pod_instance), log_response=False
        ).json()[0]["info"]
        variables = info["command"]["environment"]["variables"]
        region = next((var for var in variables if var["name"] == "REGION"), ["NO_REGION"])
        zone = next((var for var in variables if var["name"] == "ZONE"), ["NO_ZONE"])

        return region != "NO_REGION" and zone != "NO_ZONE" and len(region) > 0 and len(zone) > 0

    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 3)
    assert fault_domain_vars_are_present("hello-0")
    assert fault_domain_vars_are_present("world-0")
    assert fault_domain_vars_are_present("world-1")
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version("1.9")
@pytest.mark.smoke
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_rack_not_found():
    options = _escape_placement_for_1_9(
        {
            "service": {"yaml": "marathon_constraint"},
            "hello": {"placement": '[["rack_id", "LIKE", "rack-foo-.*"]]'},
            "world": {"placement": '[["rack_id", "LIKE", "rack-foo-.*"]]'},
        }
    )

    # scheduler should fail to deploy, don't wait for it to complete:
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        0,
        additional_options=options,
        wait_for_deployment=False,
    )
    try:
        sdk_tasks.check_running(config.SERVICE_NAME, 1, timeout_seconds=60)
        assert False, "Should have failed to deploy anything"
    except AssertionError as arg:
        raise arg
    except Exception:
        pass  # expected to fail

    pl = sdk_plan.get_deployment_plan(config.SERVICE_NAME)

    # check that everything is still stuck looking for a match:
    assert pl["status"] == "IN_PROGRESS"

    assert len(pl["phases"]) == 2

    phase1 = pl["phases"][0]
    assert phase1["status"] == "IN_PROGRESS"
    steps1 = phase1["steps"]
    assert len(steps1) == 1
    assert steps1[0]["status"] in ("PREPARED", "PENDING")  # first step may be PREPARED

    phase2 = pl["phases"][1]
    assert phase2["status"] == "PENDING"
    steps2 = phase2["steps"]
    assert len(steps2) == 2
    assert steps2[0]["status"] == "PENDING"
    assert steps2[1]["status"] == "PENDING"
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version("1.11")
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_unique_zone_fails():

    num_zones = len(set(zone for zone in sdk_utils.get_cluster_zones().values()))
    if num_zones > 1:
        # Only run if we have at least one zone.
        options = _escape_placement_for_1_9(
            {
                "service": {"yaml": "marathon_constraint"},
                "hello": {"placement": '[["@zone", "UNIQUE"]]'},
                "world": {"placement": '[["@zone", "UNIQUE"]]', "count": num_zones + 1},
            }
        )

        fail_placement(options, num_zones + 1)
    else:
        pass


@pytest.mark.dcos_min_version("1.11")
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_max_per_zone_fails():
    num_zones = len(set(zone for zone in sdk_utils.get_cluster_zones().values()))
    if num_zones > 1:
        options = _escape_placement_for_1_9(
            {
                "service": {"yaml": "marathon_constraint"},
                "hello": {"placement": '[["@zone", "MAX_PER", "1"]]'},
                "world": {"placement": '[["@zone", "MAX_PER", "1"]]', "count": num_zones + 1},
            }
        )

        fail_placement(options, num_zones + 1)
    else:
        pass


@pytest.mark.dcos_min_version("1.11")
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_max_per_zone_succeeds():
    num_zones = len(set(zone for zone in sdk_utils.get_cluster_zones().values()))
    if num_zones > 1:
        options = _escape_placement_for_1_9(
            {
                "service": {"yaml": "marathon_constraint"},
                "hello": {"placement": '[["@zone", "MAX_PER", "1"]]'},
                "world": {"placement": '[["@zone", "MAX_PER", "2"]]'},
            }
        )

        succeed_placement(options)
    else:
        pass


@pytest.mark.dcos_min_version("1.11")
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_group_by_zone_succeeds():
    num_zones = len(set(zone for zone in sdk_utils.get_cluster_zones().values()))
    if num_zones >= 3:
        options = _escape_placement_for_1_9(
            {
                "service": {"yaml": "marathon_constraint"},
                "hello": {"placement": '[["@zone", "GROUP_BY", "1"]]'},
                "world": {"placement": '[["@zone", "GROUP_BY", "1"]]', "count": 3},
            }
        )
        succeed_placement(options)
    else:
        pass


@pytest.mark.skip(reason="GROUP_BY Failing semantics need to be configured.")
@pytest.mark.dcos_min_version("1.11")
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_group_by_zone_fails():
    num_zones = len(set(zone for zone in sdk_utils.get_cluster_zones().values()))
    if num_zones > 1:
        options = _escape_placement_for_1_9(
            {
                "service": {"yaml": "marathon_constraint"},
                "hello": {"placement": '[["@zone", "GROUP_BY", "1"]]'},
                "world": {"placement": f'[["@zone", "GROUP_BY", {num_zones}]]', "count": num_zones},
            }
        )

        fail_placement(options, num_zones)
    else:
        pass


@pytest.mark.sanity
def test_hostname_unique():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    options = _escape_placement_for_1_9(
        {
            "service": {"yaml": "marathon_constraint"},
            "hello": {"count": get_num_private_agents(), "placement": '[["hostname", "UNIQUE"]]'},
            "world": {"count": get_num_private_agents(), "placement": '[["hostname", "UNIQUE"]]'},
        }
    )

    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        get_num_private_agents() * 2,
        additional_options=options,
    )

    # hello deploys first. One "world" task should end up placed with each "hello" task.
    # ensure "hello" task can still be placed with "world" task
    old_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, "hello-0")
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "pod replace hello-0")
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello-0", old_ids)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)

    sdk_tasks.check_running(
        config.SERVICE_NAME, get_num_private_agents() * 2 - 1, timeout_seconds=10
    )
    sdk_tasks.check_running(config.SERVICE_NAME, get_num_private_agents() * 2)
    ensure_count_per_agent(hello_count=1, world_count=1)


@pytest.mark.sanity
def test_max_per_hostname():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    options = _escape_placement_for_1_9(
        {
            "service": {"yaml": "marathon_constraint"},
            "hello": {
                "count": get_num_private_agents() * 2,
                "placement": '[["hostname", "MAX_PER", "2"]]',
            },
            "world": {
                "count": get_num_private_agents() * 3,
                "placement": '[["hostname", "MAX_PER", "3"]]',
            },
        }
    )

    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        get_num_private_agents() * 5,
        additional_options=options,
    )
    ensure_max_count_per_agent(hello_count=2, world_count=3)


@pytest.mark.sanity
def test_rr_by_hostname():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    options = _escape_placement_for_1_9(
        {
            "service": {"yaml": "marathon_constraint"},
            "hello": {
                "count": get_num_private_agents() * 2,
                "placement": '[["hostname", "GROUP_BY", "{}"]]'.format(get_num_private_agents()),
            },
            "world": {
                "count": get_num_private_agents() * 2,
                "placement": '[["hostname", "GROUP_BY", "{}"]]'.format(get_num_private_agents()),
            },
        }
    )

    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        get_num_private_agents() * 4,
        additional_options=options,
    )
    ensure_max_count_per_agent(hello_count=2, world_count=2)


def _escape_placement_for_1_9(options: dict) -> dict:
    # 1.9 requires `\"` to be escaped to `\\\"`
    # when submitting placement constraints
    log.info(options)
    if sdk_utils.dcos_version_at_least("1.10"):
        log.info("DC/OS version >= 1.10")
        return options

    def escape_section_placement(section: str, options: dict) -> dict:
        if section in options and "placement" in options[section]:
            options[section]["placement"] = options[section]["placement"].replace('"', '\\"')
            log.info("Escaping %s", section)

        log.info(options)
        return options

    return escape_section_placement("hello", escape_section_placement("world", options))


@pytest.mark.sanity
def test_cluster():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    some_agent = sdk_agents.get_private_agents().pop()["hostname"]
    options = _escape_placement_for_1_9(
        {
            "service": {"yaml": "marathon_constraint"},
            "hello": {
                "count": get_num_private_agents(),
                "placement": '[["hostname", "CLUSTER", "{}"]]'.format(some_agent),
            },
            "world": {"count": 0},
        }
    )

    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        get_num_private_agents(),
        additional_options=options,
    )
    ensure_count_per_agent(hello_count=get_num_private_agents(), world_count=0)


def succeed_placement(options):
    """
    This assumes that the DC/OS cluster is reporting that all agents are in a single zone.
    """
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 0, additional_options=options)
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


def fail_placement(options, world_count):
    """
    This assumes that the DC/OS cluster is reporting that all agents are in a single zone.
    """

    # scheduler should fail to deploy, don't wait for it to complete:
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        0,
        additional_options=options,
        wait_for_deployment=False,
    )
    sdk_plan.wait_for_step_status(
        config.SERVICE_NAME, "deploy", "world", f"world-{world_count-2}:[server]", "COMPLETE"
    )

    pl = sdk_plan.get_deployment_plan(config.SERVICE_NAME)

    # check that everything is still stuck looking for a match:
    assert pl["status"] == "IN_PROGRESS"

    assert len(pl["phases"]) == 2

    phase1 = pl["phases"][0]
    assert phase1["status"] == "COMPLETE"
    steps1 = phase1["steps"]
    assert len(steps1) == 1

    phase2 = pl["phases"][1]
    assert phase2["status"] == "IN_PROGRESS"
    steps2 = phase2["steps"]
    assert len(steps2) == world_count

    # This excludes the index at [0..world_count-1)
    for step in range(0, world_count - 1):
        assert steps2[step]["status"] in ("COMPLETE")
    assert steps2[world_count - 1]["status"] in ("PREPARED", "PENDING")

    try:
        # Ensure we get world_count + 1, where we have one additional hello task..
        sdk_tasks.check_running(config.SERVICE_NAME, world_count + 1, timeout_seconds=30)
        assert False, "Should have failed to deploy world-{world_count-1}"
    except AssertionError as arg:
        raise arg
    except Exception:
        pass  # expected to fail

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


def get_hello_world_agent_sets():
    hello_agents = []
    world_agents = []
    for task in sdk_tasks.get_service_tasks(config.SERVICE_NAME):
        if task.name.startswith("hello-"):
            hello_agents.append(task.agent_id)
        elif task.name.startswith("world-"):
            world_agents.append(task.agent_id)
        else:
            assert False, "Unknown task: " + task.name
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
    marathon_config["env"]["HELLO_COUNT"] = "2"
    sdk_marathon.update_app(marathon_config)

    # Now, an additional hello-server task will launch
    # where the _new_ constraint will tell it to be.
    sdk_tasks.check_running(config.SERVICE_NAME, 2)
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    assert get_task_host("hello-0-server") == some_agent
    assert get_task_host("hello-1-server") == other_agent


@pytest.mark.sanity
def test_updated_placement_constraints_no_task_change():
    some_agent, other_agent, old_ids = setup_constraint_switch()

    sdk_tasks.check_tasks_not_updated(config.SERVICE_NAME, "hello", old_ids)

    assert get_task_host("hello-0-server") == some_agent


@pytest.mark.sanity
def test_updated_placement_constraints_restarted_tasks_dont_move():
    some_agent, other_agent, old_ids = setup_constraint_switch()

    # Restart the task, and verify it doesn't move hosts
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "pod restart hello-0")
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello", old_ids)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)

    assert get_task_host("hello-0-server") == some_agent


@pytest.mark.sanity
def test_updated_placement_constraints_replaced_tasks_do_move():
    some_agent, other_agent, old_ids = setup_constraint_switch()

    # Replace the task, and verify it moves hosts
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "pod replace hello-0")
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello", old_ids)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)

    assert get_task_host("hello-0-server") == other_agent


def setup_constraint_switch():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    agents = sdk_agents.get_private_agents()
    some_agent = agents[0]["hostname"]
    other_agent = agents[1]["hostname"]
    log.info("Agents: %s %s", some_agent, other_agent)
    assert some_agent != other_agent
    options = _escape_placement_for_1_9(
        {
            "service": {"yaml": "marathon_constraint"},
            "hello": {
                "count": 1,
                # First, we stick the pod to some_agent
                "placement": '[["hostname", "LIKE", "{}"]]'.format(some_agent),
            },
            "world": {"count": 0},
        }
    )
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 1, additional_options=options)
    sdk_tasks.check_running(config.SERVICE_NAME, 1)
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, "hello")

    # Now, stick it to other_agent
    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    marathon_config["env"]["HELLO_PLACEMENT"] = '[["hostname", "LIKE", "{}"]]'.format(other_agent)
    sdk_marathon.update_app(marathon_config)
    # Wait for the scheduler to be up and settled before advancing.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    return some_agent, other_agent, hello_ids


def get_task_host(task_name):
    _, out, _ = sdk_cmd.run_cli("task {} --json".format(task_name))
    tasks_json = json.loads(out)
    matching_tasks = list(filter(lambda t: t["name"] == task_name, tasks_json))
    assert len(matching_tasks) == 1, "Duplicate tasks found with same name : [{}]".format(
        tasks_json
    )
    task_info = matching_tasks.pop()

    host = None
    for label in task_info["labels"]:
        if label["key"] == "offer_hostname":
            host = label["value"]
            break

    if host is None:
        raise Exception("offer_hostname label is not present!: {}".format(task_info))

    # Validation: Check that label matches summary returned by CLI
    for task in sdk_tasks.get_summary():
        if task.name == task_name:
            if task.host == host:
                # OK!
                return host
            else:
                # CLI's hostname doesn't match the TaskInfo labels. Bug!
                raise Exception(
                    "offer_hostname label [{}] doesn't match CLI output [{}]\nTask:\n{}".format(
                        host, task.host, task_info
                    )
                )

    # Unable to find desired task in CLI!
    raise Exception("Unable to find task named {} in CLI".format(task_name))


@functools.lru_cache()
def get_num_private_agents():
    return len(sdk_agents.get_private_agents())
