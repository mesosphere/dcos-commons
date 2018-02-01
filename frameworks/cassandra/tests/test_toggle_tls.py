import json
import logging
import pytest
import shakedown
import tempfile

import sdk_cmd
import sdk_install
import sdk_jobs
import sdk_plan
import sdk_security
import sdk_utils

from security import transport_encryption

from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module')
def service_account(configure_security):
    """
    Creates service account and secret and yields dict containing both.
    """
    try:
        name = config.SERVICE_NAME
        secret = "{}-secret".format(name)
        sdk_security.create_service_account(
            service_account_name=name, service_account_secret=secret)
        # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
        sdk_cmd.run_cli(
            "security org groups add_user superusers {name}".format(name=name))
        yield {"name": name, "secret": secret}
    finally:
        sdk_security.delete_service_account(
            service_account_name=name, service_account_secret=secret)


@pytest.fixture(scope='module')
def dcos_ca_bundle():
    """
    Retrieve DC/OS CA bundle and returns the content.
    """
    resp = sdk_cmd.cluster_request('GET', '/ca/dcos-ca.crt')
    cert = resp.content.decode('ascii')
    assert cert is not None
    return cert


@pytest.fixture(scope='module', autouse=True)
def cassandra_service(service_account):
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
            wait_for_deployment=True)

        # Wait for service health check to pass
        shakedown.service_healthy(config.SERVICE_NAME)

        yield {**options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.tls
@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
def test_default_installation(cassandra_service):
    """
    Tests writing, reading and deleting data over a plaintext connection.
    """
    verify_client_can_write_read_and_delete()


@pytest.mark.sanity
@pytest.mark.tls
@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
def test_enable_tls_and_plaintext(cassandra_service, dcos_ca_bundle):
    """
    Tests writing, reading and deleting data over TLS but still accepting
    plaintext connections.
    """
    update_options = {
        "service": {
            "security": {
                "transport_encryption": {
                    "enabled": True,
                    "allow_plaintext": True
                }
            }
        }
    }

    update_service(cassandra_service["package_name"],
                   cassandra_service["service"]["name"], update_options)

    verify_client_can_write_read_and_delete(dcos_ca_bundle)


@pytest.mark.sanity
@pytest.mark.tls
@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
def test_disable_plaintext(cassandra_service, dcos_ca_bundle):
    """
    Tests writing, reading and deleting data over a TLS connection.
    """
    update_options = {
        "service": {
            "security": {
                "transport_encryption": {
                    "enabled": True,
                    "allow_plaintext": False
                }
            }
        }
    }

    update_service(cassandra_service["package_name"],
                   cassandra_service["service"]["name"], update_options)

    verify_client_can_write_read_and_delete(dcos_ca_bundle)


@pytest.mark.sanity
@pytest.mark.tls
@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
def test_disable_tls(cassandra_service):
    """
    Tests writing, reading and deleting data over a plaintext connection.
    """
    update_options = {
        "service": {
            "security": {
                "transport_encryption": {
                    "enabled": False,
                    "allow_plaintext": False
                }
            }
        }
    }

    update_service(cassandra_service["package_name"],
                   cassandra_service["service"]["name"], update_options)

    verify_client_can_write_read_and_delete()


def verify_client_can_write_read_and_delete(dcos_ca_bundle=None):
    with sdk_jobs.InstallJobContext([
            config.get_write_data_job(dcos_ca_bundle=dcos_ca_bundle),
            config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle),
            config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle),
            config.get_verify_deletion_job(dcos_ca_bundle=dcos_ca_bundle)
    ]):
        sdk_jobs.run_job(
            config.get_write_data_job(dcos_ca_bundle=dcos_ca_bundle))
        sdk_jobs.run_job(
            config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle))
        sdk_jobs.run_job(
            config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle))
        sdk_jobs.run_job(
            config.get_verify_deletion_job(dcos_ca_bundle=dcos_ca_bundle))


def update_service(package_name: str, service_name: str, options: dict):
    with tempfile.NamedTemporaryFile("w", suffix=".json") as f:
        options_path = f.name

        log.info("Writing updated options to %s", options_path)
        json.dump(options, f)
        f.flush()

        cmd = ["update", "start", "--options={}".format(options_path)]
        sdk_cmd.svc_cli(package_name, service_name, " ".join(cmd))

        # An update plan is a deploy plan
        sdk_plan.wait_for_kicked_off_deployment(service_name)
        sdk_plan.wait_for_completed_deployment(service_name)
