import pytest
import shakedown

import sdk_cmd
import sdk_install
import sdk_hosts
import sdk_recovery
import sdk_utils

from security import transport_encryption

from tests import config

pytestmark = [pytest.mark.skipif(sdk_utils.is_open_dcos(),
                                 reason="Feature only supported in DC/OS EE"),
              pytest.mark.skipif(sdk_utils.dcos_version_less_than("1.10"),
                                 reason="TLS tests require DC/OS 1.10+")]


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
        transport_encryption.cleanup_service_account(config.SERVICE_NAME,
                                                     service_account_info)


@pytest.fixture(scope='module')
def elastic_service(service_account):
    service_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
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

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            service_name=config.SERVICE_NAME,
            expected_running_tasks=config.DEFAULT_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60)

        yield {**service_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope='module')
def kibana_application(elastic_service):
    try:
        elasticsearch_url = "https://" + sdk_hosts.vip_host(config.SERVICE_NAME, "coordinator", 9200)

        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_SERVICE_NAME)
        sdk_install.install(
            config.KIBANA_PACKAGE_NAME,
            service_name=config.KIBANA_SERVICE_NAME,
            expected_running_tasks=0,
            additional_options={
                "kibana": {
                    "xpack_enabled": True,
                    "elasticsearch_tls": True,
                    "elasticsearch_url": elasticsearch_url
                }
            },
            timeout_seconds=config.KIBANA_DEFAULT_TIMEOUT,
            wait_for_deployment=False)

        yield
    finally:
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_SERVICE_NAME)


@pytest.mark.tls
@pytest.mark.smoke
def test_healthy(elastic_service):
    assert shakedown.service_healthy(config.SERVICE_NAME)


@pytest.mark.tls
@pytest.mark.sanity
def test_crud_over_tls(elastic_service):
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


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.skipif(sdk_utils.dcos_version_at_least('1.12'),
                    reason='MESOS-9008: Mesos Fetcher fails to extract Kibana archive')
def test_kibana_tls(kibana_application):
    config.check_kibana_adminrouter_integration("service/{}/login".format(config.KIBANA_SERVICE_NAME))


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.recovery
def test_tls_recovery(elastic_service, service_account):
    pod_list = sdk_cmd.svc_cli(elastic_service["package_name"],
                               elastic_service["service"]["name"],
                               "pod list",
                               json=True)

    for pod in pod_list:
        sdk_recovery.check_permanent_recovery(elastic_service["package_name"],
                                              elastic_service["service"]["name"],
                                              pod,
                                              recovery_timeout_s=25 * 60)
