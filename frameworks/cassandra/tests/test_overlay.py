import tempfile

import pytest
import sdk_install
import sdk_jobs
import sdk_networks
import sdk_plan
import sdk_utils
import shakedown
from tests import config


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    test_jobs = []
    try:
        test_jobs = config.get_all_jobs()
        sdk_install.uninstall(config.PACKAGE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS)

        tmp_dir = tempfile.mkdtemp(prefix='cassandra-test')
        for job in test_jobs:
            sdk_jobs.install_job(job, tmp_dir=tmp_dir)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME)

        for job in test_jobs:
            sdk_jobs.remove_job(job)


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
@sdk_utils.dcos_1_9_or_higher
def test_service_overlay_health():
    shakedown.service_healthy(config.PACKAGE_NAME)
    node_tasks = (
        "node-0-server",
        "node-1-server",
        "node-2-server",
    )
    for task in node_tasks:
        sdk_networks.check_task_network(task)


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
@sdk_utils.dcos_1_9_or_higher
def test_functionality():
    parameters = {'CASSANDRA_KEYSPACE': 'testspace1'}

    # populate 'testspace1' for test, then delete afterwards:
    with sdk_jobs.RunJobContext(
            before_jobs=[
                config.get_write_data_job(),
                config.get_verify_data_job()
            ],
            after_jobs=[
                config.get_delete_data_job(),
                config.get_verify_deletion_job()
            ]):

        sdk_plan.start_plan(config.PACKAGE_NAME, 'cleanup', parameters=parameters)
        sdk_plan.wait_for_completed_plan(config.PACKAGE_NAME, 'cleanup')

        sdk_plan.start_plan(config.PACKAGE_NAME, 'repair', parameters=parameters)
        sdk_plan.wait_for_completed_plan(config.PACKAGE_NAME, 'repair')


@pytest.mark.sanity
@pytest.mark.overlay
@sdk_utils.dcos_1_9_or_higher
def test_endpoints():
    endpoints = sdk_networks.get_and_test_endpoints("", config.PACKAGE_NAME, 1)  # tests that the correct number of endpoints are found, should just be "node"
    assert "node" in endpoints, "Cassandra endpoints should contain only 'node', got {}".format(endpoints)
    endpoints = sdk_networks.get_and_test_endpoints("node", config.PACKAGE_NAME, 3)
    assert "address" in endpoints, "Endpoints missing address key"
    sdk_networks.check_endpoints_on_overlay(endpoints)
