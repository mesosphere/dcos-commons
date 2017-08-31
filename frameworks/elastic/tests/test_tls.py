import pytest
import shakedown

import sdk_cmd
import sdk_install
import sdk_plan
import sdk_security
from tests import config
from tests.config import (
    PACKAGE_NAME,
    NO_INGEST_TASK_COUNT,
)


@pytest.fixture(scope='module', autouse=True)
def client_over_tls():
    """
    This fixture forces all requests from config.py module to be issued over
    TLS connection.
    """
    original_value = config.ELASTIC_TLS_ENABLED
    config.ELASTIC_TLS_ENABLED = True
    yield
    config.ELASTIC_TLS_ENABLED = original_value


@pytest.fixture(scope='module')
def service_account():
    """
    Creates service account with `elastic` name and yields the name.
    """
    name = PACKAGE_NAME
    sdk_security.create_service_account(
        service_account_name=name, service_account_secret=name)
     # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
    sdk_cmd.run_cli(
        "security org groups add_user superusers {name}".format(name=name))
    yield name
    sdk_security.delete_service_account(
        service_account_name=name, service_account_secret=name)


@pytest.fixture(scope='module')
def elastic_service_tls(service_account):
    sdk_install.install(
        PACKAGE_NAME,
        service_name=service_account,
        expected_running_tasks=NO_INGEST_TASK_COUNT,
        additional_options={
            "service": {
                "service_account_secret": service_account,
                "service_account": service_account,
                "tls": True,
            },
            "elasticsearch": {
                "xpack_enabled": True,
            }
        }
    )

    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)

    # Wait for service health check to pass
    shakedown.service_healthy(PACKAGE_NAME)

    yield

    sdk_install.uninstall(PACKAGE_NAME)


@pytest.mark.smoke
def test_healthy(elastic_service_tls):
    assert shakedown.service_healthy(PACKAGE_NAME)


@pytest.mark.tls
@pytest.mark.sanity
def test_crud_over_tls(elastic_service_tls):
    config.create_index(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_SETTINGS_MAPPINGS,
        service_name=PACKAGE_NAME)
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        1,
        {"name": "Loren", "role": "developer"},
        service_name=PACKAGE_NAME)
    config.get_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        1)
    config.delete_index(
        config.DEFAULT_INDEX_NAME, service_name=PACKAGE_NAME)
