import json
import pytest

import sdk_cmd
import sdk_install
import sdk_hosts
import sdk_recovery
import sdk_utils

from security import transport_encryption

from tests import config

pytestmark = [
    sdk_utils.dcos_ee_only,
    pytest.mark.skipif(
        sdk_utils.dcos_version_less_than("1.10"), reason="TLS tests require DC/OS 1.10+"
    ),
]


@pytest.fixture(scope="module")
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        name = config.SERVICE_NAME
        service_account_info = transport_encryption.setup_service_account(name)

        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(config.SERVICE_NAME, service_account_info)


@pytest.fixture(scope="module")
def elastic_service(service_account):
    package_name = config.PACKAGE_NAME
    service_name = config.SERVICE_NAME
    expected_running_tasks = config.DEFAULT_TASK_COUNT

    service_options = {
        "service": {
            "name": service_name,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
            "security": {"transport_encryption": {"enabled": True}},
        },
        "elasticsearch": {"xpack_security_enabled": True},
    }

    sdk_install.uninstall(package_name, service_name)

    try:
        sdk_install.install(
            package_name,
            service_name=service_name,
            expected_running_tasks=expected_running_tasks,
            additional_options=service_options,
            timeout_seconds=30 * 60,
        )

        # Start trial license.
        config.start_trial_license(service_name, https=True)

        # Set up passwords. Basic HTTP credentials will have to be used in HTTP requests to
        # Elasticsearch from now on.
        passwords = config.setup_passwords(service_name, https=True)

        # Set up healthcheck basic HTTP credentials.
        sdk_service.update_configuration(
            package_name,
            service_name,
            {
                "elasticsearch": {"health_user_password": passwords["elastic"]},
            },
            expected_running_tasks,
        )

        yield {**service_options, **{"package_name": package_name}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope="module")
def kibana_application(elastic_service):
    try:
        elasticsearch_url = "https://" + sdk_hosts.vip_host(
            config.SERVICE_NAME, "coordinator", 9200
        )

        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_SERVICE_NAME)
        sdk_install.install(
            config.KIBANA_PACKAGE_NAME,
            service_name=config.KIBANA_SERVICE_NAME,
            expected_running_tasks=0,
            additional_options={
                "kibana": {
                    "elasticsearch_xpack_security_enabled": True,
                    "elasticsearch_tls": True,
                    "elasticsearch_url": elasticsearch_url,
                }
            },
            timeout_seconds=config.KIBANA_DEFAULT_TIMEOUT,
            wait_for_deployment=False,
        )

        yield
    finally:
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_SERVICE_NAME)


@pytest.mark.tls
@pytest.mark.sanity
def test_crud_over_tls(elastic_service):
    config.create_index(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_SETTINGS_MAPPINGS,
        service_name=config.SERVICE_NAME,
        https=True,
    )
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        1,
        {"name": "Loren", "role": "developer"},
        service_name=config.SERVICE_NAME,
        https=True,
    )
    document = config.get_document(
        config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 1, https=True
    )

    assert document
    assert document["_source"]["name"] == "Loren"


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.skip(
    message="Kibana 6.3 with TLS enabled is not working due Admin Router request header. Details in https://jira.mesosphere.com/browse/DCOS-43386"
)
def test_kibana_tls(kibana_application):
    config.check_kibana_adminrouter_integration(
        "service/{}/login".format(config.KIBANA_SERVICE_NAME)
    )


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.recovery
def test_tls_recovery(elastic_service, service_account):
    rc, stdout, _ = sdk_cmd.svc_cli(
        elastic_service["package_name"], elastic_service["service"]["name"], "pod list"
    )
    assert rc == 0, "Pod list failed"

    for pod in json.loads(stdout):
        sdk_recovery.check_permanent_recovery(
            elastic_service["package_name"],
            elastic_service["service"]["name"],
            pod,
            recovery_timeout_s=25 * 60,
        )
