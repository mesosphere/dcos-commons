import pytest
import shakedown

import sdk_install
import sdk_plan
import sdk_utils

from security import transport_encryption

from tests import config

pytestmark = pytest.mark.skipif(sdk_utils.is_open_dcos(),
                                reason='Feature only supported in DC/OS EE')


@pytest.fixture(scope='module')
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        name = config.SERVICE_NAME
        service_account_info = transport_encryption.setup_service_account(name)

        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(service_account_info)


@pytest.fixture(scope='module')
def elastic_service_tls(service_account):
    sdk_install.install(
        config.PACKAGE_NAME,
        service_name=config.SERVICE_NAME,
        expected_running_tasks=config.DEFAULT_TASK_COUNT,
        additional_options={
            "service": {
                "service_account_secret": service_account["name"],
                "service_account": service_account["secret"],
                "security": {
                    "transport_encryption": {
                        "enabled": True
                    }
                }
            },
            "elasticsearch": {
                "xpack_enabled": True,
            }
        }
    )

    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    # Wait for service health check to pass
    shakedown.service_healthy(config.SERVICE_NAME)

    yield

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.tls
@pytest.mark.smoke
@pytest.mark.dcos_min_version('1.10')
def test_healthy(elastic_service_tls):
    assert shakedown.service_healthy(config.SERVICE_NAME)


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.dcos_min_version('1.10')
def test_crud_over_tls(elastic_service_tls):
    config.create_index(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_SETTINGS_MAPPINGS,
        service_name=config.SERVICE_NAME,
        https=True)
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        1,
        {"name": "Loren", "role": "developer"},
        service_name=config.SERVICE_NAME,
        https=True)
    document = config.get_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        1,
        https=True)

    assert document
    assert document['_source']['name'] == 'Loren'
