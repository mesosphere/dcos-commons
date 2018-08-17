import pytest
import sdk_install
import sdk_jobs
import sdk_networks
import sdk_plan
import sdk_tasks
from tests import config


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    test_jobs = []
    try:
        test_jobs = config.get_all_jobs()
        # destroy/reinstall any prior leftover jobs, so that they don't touch the newly installed service:
        for job in test_jobs:
            sdk_jobs.install_job(job)

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS,
        )

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        for job in test_jobs:
            sdk_jobs.remove_job(job)


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
@pytest.mark.dcos_min_version("1.9")
def test_service_overlay_health():
    tasks = sdk_tasks.check_task_count(config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)
    for task in tasks:
        sdk_networks.check_task_network(task.name)


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
@pytest.mark.dcos_min_version("1.9")
def test_functionality():
    parameters = {"CASSANDRA_KEYSPACE": "testspace1"}

    # populate 'testspace1' for test, then delete afterwards:
    with sdk_jobs.RunJobContext(
        before_jobs=[config.get_write_data_job(), config.get_verify_data_job()],
        after_jobs=[config.get_delete_data_job(), config.get_verify_deletion_job()],
    ):

        sdk_plan.start_plan(config.SERVICE_NAME, "cleanup", parameters=parameters)
        sdk_plan.wait_for_completed_plan(config.SERVICE_NAME, "cleanup")

        sdk_plan.start_plan(config.SERVICE_NAME, "repair", parameters=parameters)
        sdk_plan.wait_for_completed_plan(config.SERVICE_NAME, "repair")


@pytest.mark.nick
@pytest.mark.overlay
@pytest.mark.dcos_min_version("1.9")
def test_endpoints():
    endpoint_names = sdk_networks.get_endpoint_names(config.PACKAGE_NAME, config.SERVICE_NAME)
    assert set(endpoint_names) == set(["native-client"])

    sdk_networks.check_endpoint_on_overlay(config.PACKAGE_NAME, config.SERVICE_NAME, "native-client", config.DEFAULT_TASK_COUNT)
