import logging

import pytest
import retrying
import sdk_install
import sdk_marathon
import sdk_tasks
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        # don't wait for install to complete successfully:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            0,
            {"service": {"yaml": "taskcfg"}},
            wait_for_deployment=False,
        )

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_deploy():
    wait_time = 30
    # taskcfg.yml will initially fail to deploy because several options are missing in the default
    # sdk_marathon.json.mustache. verify that the tasks are failing before continuing.
    task_name = "hello-0-server"
    log.info("Checking that {} is failing to launch within {}s".format(task_name, wait_time))

    original_state_history = _get_state_history(task_name)

    # wait for new TASK_FAILEDs to appear:
    @retrying.retry(
        wait_fixed=1000, stop_max_delay=1000 * wait_time, retry_on_result=lambda res: not res
    )
    def wait_for_new_failures():
        new_state_history = _get_state_history(task_name)
        assert len(new_state_history) >= len(original_state_history)

        added_state_history = new_state_history[len(original_state_history) :]
        log.info("Added {} state history: {}".format(task_name, ", ".join(added_state_history)))
        return "TASK_FAILED" in added_state_history

    wait_for_new_failures()

    # add the needed envvars in marathon and confirm that the deployment succeeds:
    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    env = marathon_config["env"]
    del env["SLEEP_DURATION"]
    env["TASKCFG_ALL_OUTPUT_FILENAME"] = "output"
    env["TASKCFG_ALL_SLEEP_DURATION"] = "1000"
    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)

    config.check_running()


def _get_state_history(task_name):
    return [s["state"] for s in sdk_tasks.get_all_status_history(task_name)]
