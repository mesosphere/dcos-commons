import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install
import sdk_marathon
import sdk_tasks
import sdk_utils
from tests import config


# This file tests installing the elastic framework with the new default of 0 ingest nodes.
# We can't test it in `test_sanity.py` because the upgrade test in `configure_package` won't work,
# as the v1.0.8-5.2.2 package does not allow 0 ingest nodes due to the JSON restriction in `config.json` where
# `ingest_nodes.count.minimum` = 1. Once beta-elastic gets promoted to Universe elastic, we can
# 1) delete this file
# 2) remove `"ingest_nodes": {"count": 1}` from `configure_package` in `test_sanity.py`


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, config.NO_INGEST_TASK_COUNT)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_service_health():
    sdk_tasks.check_running(config.SERVICE_NAME, config.NO_INGEST_TASK_COUNT)
    config.wait_for_expected_nodes_to_exist(task_count=config.NO_INGEST_TASK_COUNT)
    assert shakedown.service_healthy(config.SERVICE_NAME)


@pytest.mark.recovery
@pytest.mark.sanity
def test_zero_to_one_ingest():
    sdk_tasks.check_running(config.SERVICE_NAME, config.NO_INGEST_TASK_COUNT)
    config.wait_for_expected_nodes_to_exist(task_count=config.NO_INGEST_TASK_COUNT)
    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    marathon_config['env']['INGEST_NODE_COUNT'] = "1"
    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)
    sdk_tasks.check_running(config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)
    config.wait_for_expected_nodes_to_exist()


@pytest.mark.recovery
@pytest.mark.sanity
def test_ingest_node_replace():
    sdk_tasks.check_running(config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)
    config.wait_for_expected_nodes_to_exist()
    ingest_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'ingest-0')
    cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace ingest-0')
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'ingest-0', ingest_ids)
    sdk_tasks.check_running(config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)
    config.wait_for_expected_nodes_to_exist()
