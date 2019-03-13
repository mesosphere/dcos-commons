import json
import logging
import pytest
import tempfile
from typing import Any, Dict, Iterable, Optional

import sdk_cmd
import sdk_install
import sdk_jobs
import sdk_plan
import sdk_utils

from security import transport_encryption

from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module")
def service_account(configure_security: None) -> Iterable[Dict[str, Any]]:
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
def dcos_ca_bundle() -> str:
    """
    Retrieve DC/OS CA bundle and returns the content.
    """
    return transport_encryption.fetch_dcos_ca_bundle_contents()


@pytest.fixture(scope="module", autouse=True)
def cassandra_service(service_account: Dict[str, Any]) -> Iterable[Dict[str, Any]]:
    """
    A pytest fixture that installs the cassandra service.
    On teardown, the service is uninstalled.
    """
    options = {
        "service": {
            "name": config.SERVICE_NAME,
            # Note that since we wish to toggle TLS which *REQUIRES* a service account,
            # we need to install Cassandra with a service account to start with.
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=options,
            wait_for_deployment=True,
        )

        yield {**options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.tls
@pytest.mark.dcos_min_version("1.10")
@sdk_utils.dcos_ee_only
def test_default_installation(cassandra_service: Dict[str, Any]) -> None:
    """
    Tests writing, reading and deleting data over a plaintext connection.
    """
    verify_client_can_write_read_and_delete()


@pytest.mark.sanity
@pytest.mark.tls
@pytest.mark.dcos_min_version("1.10")
@sdk_utils.dcos_ee_only
def test_enable_tls_and_plaintext(
    cassandra_service: Dict[str, Any],
    dcos_ca_bundle: str,
) -> None:
    """
    Tests writing, reading and deleting data over TLS but still accepting
    plaintext connections.
    """
    update_service_transport_encryption(cassandra_service, enabled=True, allow_plaintext=True)
    verify_client_can_write_read_and_delete(dcos_ca_bundle)


@pytest.mark.sanity
@pytest.mark.tls
@pytest.mark.dcos_min_version("1.10")
@sdk_utils.dcos_ee_only
def test_disable_plaintext(
    cassandra_service: Dict[str, Any],
    dcos_ca_bundle: str,
) -> None:
    """
    Tests writing, reading and deleting data over a TLS connection.
    """
    update_service_transport_encryption(cassandra_service, enabled=True, allow_plaintext=False)
    verify_client_can_write_read_and_delete(dcos_ca_bundle)


@pytest.mark.sanity
@pytest.mark.tls
@pytest.mark.dcos_min_version("1.10")
@sdk_utils.dcos_ee_only
def test_disable_tls(cassandra_service: Dict[str, Any]) -> None:
    """
    Tests writing, reading and deleting data over a plaintext connection.
    """
    update_service_transport_encryption(cassandra_service, enabled=False, allow_plaintext=False)
    verify_client_can_write_read_and_delete()


@pytest.mark.sanity
@pytest.mark.tls
@pytest.mark.dcos_min_version("1.10")
@sdk_utils.dcos_ee_only
def test_enabling_then_disabling_tls(
    cassandra_service: Dict[str, Any],
    dcos_ca_bundle: str,
) -> None:
    # Write data.
    write_data_job = config.get_write_data_job()
    with sdk_jobs.InstallJobContext([write_data_job]):
        sdk_jobs.run_job(write_data_job)

    # Turn TLS on and off again.
    update_service_transport_encryption(cassandra_service, enabled=True, allow_plaintext=True)
    update_service_transport_encryption(cassandra_service, enabled=True, allow_plaintext=False)
    update_service_transport_encryption(cassandra_service, enabled=False, allow_plaintext=False)

    # Make sure data is still there.
    verify_data_job = config.get_verify_data_job()
    with sdk_jobs.InstallJobContext([verify_data_job]):
        sdk_jobs.run_job(verify_data_job)


def verify_client_can_write_read_and_delete(
    dcos_ca_bundle: Optional[str] = None,
) -> None:
    write_data_job = config.get_write_data_job(dcos_ca_bundle=dcos_ca_bundle)
    verify_data_job = config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle)
    delete_data_job = config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle)
    verify_deletion_job = config.get_verify_deletion_job(dcos_ca_bundle=dcos_ca_bundle)

    with sdk_jobs.InstallJobContext(
        [write_data_job, verify_data_job, delete_data_job, verify_deletion_job]
    ):
        sdk_jobs.run_job(write_data_job)
        sdk_jobs.run_job(verify_data_job)
        sdk_jobs.run_job(delete_data_job)
        sdk_jobs.run_job(verify_deletion_job)


def update_service_transport_encryption(
    cassandra_service: Dict[str, Any],
    enabled: bool = False,
    allow_plaintext: bool = False
) -> None:
    update_options = {
        "service": {
            "security": {
                "transport_encryption": {"enabled": enabled, "allow_plaintext": allow_plaintext}
            }
        }
    }

    update_service(cassandra_service, update_options)


def update_service(service: Dict[str, Any], options: Dict[str, Any]) -> None:
    with tempfile.NamedTemporaryFile("w", suffix=".json") as f:
        options_path = f.name

        log.info("Writing updated options to %s", options_path)
        json.dump(options, f)
        f.flush()

        cmd = ["update", "start", "--options={}".format(options_path)]
        sdk_cmd.svc_cli(service["package_name"], service["service"]["name"], " ".join(cmd))

        # An update plan is a deploy plan
        sdk_plan.wait_for_kicked_off_deployment(service["service"]["name"])
        sdk_plan.wait_for_completed_deployment(service["service"]["name"])
