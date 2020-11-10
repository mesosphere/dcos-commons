import logging
import pytest
import re

import sdk_agents
import sdk_install
import sdk_plan
import sdk_tasks
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.external_volumes
@pytest.mark.sanity
@pytest.mark.dcos_min_version("2.1")
def test_default_deployment():
    # Test default installation with external volumes.
    # Ensure service comes up successfully.
    options = {
        "service": {
            "yaml": "external-volumes",
            "external_volumes": {
                "pod-replacement-failure-policy": {
                    "enable-automatic-pod-replacement": True,
                    "permanent-failure-timeout-secs": 30,
                }
            },
        }
    }
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        3,
        additional_options=options,
        wait_for_deployment=True,
    )
    # Wait for scheduler to restart.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)


@pytest.mark.external_volumes
@pytest.mark.sanity
def test_auto_replace_on_drain():
    candidate_tasks = sdk_tasks.get_tasks_avoiding_scheduler(
        config.SERVICE_NAME, re.compile("^(hello|world)-[0-9]+-server$")
    )

    assert len(candidate_tasks) != 0, "Could not find a node to drain"

    # Pick the host of the first task from the above list
    replace_agent_id = candidate_tasks[0].agent_id
    replace_tasks = [task for task in candidate_tasks if task.agent_id == replace_agent_id]
    log.info(
        "Tasks on agent {} to be replaced after drain: {}".format(replace_agent_id, replace_tasks)
    )
    sdk_agents.drain_agent(replace_agent_id)

    sdk_plan.wait_for_kicked_off_recovery(config.SERVICE_NAME)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)

    new_tasks = sdk_tasks.get_summary()

    for replaced_task in replace_tasks:
        new_task = [
            task
            for task in new_tasks
            if task.name == replaced_task.name and task.id != replaced_task.id
        ][0]
        log.info(
            "Checking affected task has moved to a new agent:\n"
            "old={}\nnew={}".format(replaced_task, new_task)
        )
        assert replaced_task.agent_id != new_task.agent_id

    # Reactivate the drained agent, otherwise uninstall plans will be halted for portworx
    sdk_agents.reactivate_agent(replace_agent_id)
