import json
import pytest
from toolz import get_in
from typing import Any, Dict, Iterator

import sdk_cmd
import sdk_install
import sdk_hosts
import sdk_recovery
import sdk_service
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
def service_account(configure_security: None) -> Iterator[Dict[str, Any]]:
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
def elastic_service(service_account: Dict[str, Any]) -> Iterator[Dict[str, Any]]:
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

    try:
        sdk_install.uninstall(package_name, service_name)

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
            {"elasticsearch": {"health_user_password": passwords["elastic"]}},
            expected_running_tasks,
        )

        yield {**service_options, **{"package_name": package_name, "passwords": passwords}}
    finally:
        sdk_install.uninstall(package_name, service_name)


@pytest.fixture(scope="module")
def kibana_application(elastic_service: Dict[str, Any]) -> Iterator[Dict[str, Any]]:
    package_name = config.KIBANA_PACKAGE_NAME
    service_name = config.KIBANA_SERVICE_NAME

    elasticsearch_url = "https://" + sdk_hosts.vip_host(
        elastic_service["service"]["name"], "coordinator", 9200
    )

    service_options = {
        "service": {
            "name": service_name,
        },
        "kibana": {
            "elasticsearch_tls": True,
            "elasticsearch_url": elasticsearch_url,
            "elasticsearch_xpack_security_enabled": True,
            "password": elastic_service["passwords"]["kibana"],
        }
    }

    try:
        sdk_install.uninstall(package_name, service_name)

        sdk_install.install(
            package_name,
            service_name=service_name,
            expected_running_tasks=0,
            additional_options=service_options,
            timeout_seconds=config.KIBANA_DEFAULT_TIMEOUT,
            wait_for_deployment=False,
        )

        yield {**service_options, **{"package_name": package_name, "elastic": elastic_service}}
    finally:
        sdk_install.uninstall(package_name, service_name)


@pytest.mark.tls
@pytest.mark.sanity
def test_crud_over_tls(elastic_service: Dict[str, Any]) -> None:
    service_name = elastic_service["service"]["name"]
    http_password = elastic_service["passwords"]["elastic"]
    index_name = config.DEFAULT_INDEX_NAME
    index_type = config.DEFAULT_INDEX_TYPE
    index = config.DEFAULT_SETTINGS_MAPPINGS
    document_fields = {"name": "Loren", "role": "developer"}
    document_id = 1

    config.create_index(
        index_name, index, service_name=service_name, https=True, http_password=http_password
    )

    config.create_document(
        index_name,
        index_type,
        document_id,
        document_fields,
        service_name=service_name,
        https=True,
        http_password=http_password,
    )

    document = config.get_document(
        index_name, index_type, document_id, https=True, http_password=http_password
    )

    assert get_in(["_source", "name"], document) == document_fields["name"]


@pytest.mark.tls
@pytest.mark.sanity
def test_kibana_tls(kibana_application: Dict[str, Any]) -> None:
    config.check_kibana_adminrouter_integration(
        "service/{}/login".format(kibana_application["service"]["name"])
    )


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.recovery
def test_tls_recovery(elastic_service: Dict[str, Any], service_account: Dict[str, Any]) -> None:
    service_name = elastic_service["service"]["name"]
    package_name = elastic_service["package_name"]

    rc, stdout, _ = sdk_cmd.svc_cli(package_name, service_name, "pod list")

    assert rc == 0, "Pod list failed"

    for pod in json.loads(stdout):
        sdk_recovery.check_permanent_recovery(
            package_name, service_name, pod, recovery_timeout_s=25 * 60
        )
