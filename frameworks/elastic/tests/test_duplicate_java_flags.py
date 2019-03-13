import pytest
from tests import config

@pytest.fixture(scope="module")
def elastic_service(service_account):
    service_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
            "security": {"transport_encryption": {"enabled": True}},
        },
        "elasticsearch": {"xpack_enabled": True},
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            service_name=config.SERVICE_NAME,
            expected_running_tasks=config.DEFAULT_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
        )

        yield {**service_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

def test_duplicate_flags(elastic_service):
	'''Checking of the flag Xmx1g is present in the task params '''
	task_name = "data-1-node"
	exit_code, stdout, stderr = sdk_cmd.service_task_exec(service_name, task_name, curl_cmd)
	assert stdout.find('Xmx1g') >=0, "Default flag is not present"


