import pytest

import sdk_install
from tests.config import *

log = logging.getLogger(__name__)

# This file tests installing the elastic framework with the new default of 0 ingest nodes.
# We can't test it in `test_sanity.py` because the upgrade test in `configure_package` won't work,
# as the v1.0.8-5.2.2 package does not allow 0 ingest nodes due to the JSON restriction in `config.json` where
# `ingest_nodes.count.minimum` = 1. Once beta-elastic gets promoted to Universe elastic, we can
# 1) delete this file
# 2) remove `"ingest_nodes": {"count": 1}` from `configure_package` in `test_sanity.py`


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(PACKAGE_NAME)
        sdk_install.install(PACKAGE_NAME, NO_INGEST_TASK_COUNT)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME)


@pytest.fixture(autouse=True)
def pre_test_setup():
    sdk_tasks.check_running(PACKAGE_NAME, NO_INGEST_TASK_COUNT)
    wait_for_expected_nodes_to_exist(task_count=NO_INGEST_TASK_COUNT)


@pytest.mark.sanity
def test_service_health():
    assert shakedown.service_healthy(PACKAGE_NAME)


@pytest.mark.recovery
@pytest.mark.sanity
def test_zero_to_one_ingest():
    config = sdk_marathon.get_config(PACKAGE_NAME)
    config['env']['INGEST_NODE_COUNT'] = "1"
    sdk_marathon.update_app(PACKAGE_NAME, config)
    sdk_tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)
    wait_for_expected_nodes_to_exist(task_count=DEFAULT_TASK_COUNT)
