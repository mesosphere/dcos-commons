import pytest
import shakedown

import sdk_cmd
import sdk_install
import sdk_plan
import sdk_security
import sdk_utils
from tests import config
from tests.config import (
    PACKAGE_NAME,
    NO_INGEST_TASK_COUNT,
    SERVICE_NAME,
)


@pytest.fixture(scope='module')
def service_account():
    """
    Creates service account with `elastic` name and yields the name.
    """
    name = SERVICE_NAME
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
        service_name=SERVICE_NAME,
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

    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Wait for service health check to pass
    shakedown.service_healthy(SERVICE_NAME)

    yield

    sdk_install.uninstall(PACKAGE_NAME, SERVICE_NAME)


@pytest.mark.tls
@pytest.mark.smoke
@sdk_utils.dcos_1_10_or_higher
def test_healthy(elastic_service_tls):
    assert shakedown.service_healthy(SERVICE_NAME)


@pytest.mark.tls
@pytest.mark.sanity
@sdk_utils.dcos_1_10_or_higher
def test_crud_over_tls(elastic_service_tls):
    config.create_index(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_SETTINGS_MAPPINGS,
        service_name=SERVICE_NAME,
        https=True)
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        1,
        {"name": "Loren", "role": "developer"},
        service_name=SERVICE_NAME,
        https=True)
    document = config.get_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        1,
        https=True)

    assert document
    assert document['_source']['name'] == 'Loren'
