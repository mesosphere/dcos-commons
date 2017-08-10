import json

import pytest

import sdk_cmd
import sdk_install
import sdk_networks
import sdk_plan
import sdk_security
import sdk_utils


from tests.test_utils import  *

# Name of the broker TLS vip
BROKER_TLS_ENDPOINT = 'broker-tls'


@pytest.fixture(scope='module')
def dcos_security_cli():
    """
    Installs the dcos enterprise cli.
    """

    sdk_cmd.run_cli("package install --yes dcos-enterprise-cli")


@pytest.fixture(scope='module')
def service_account(dcos_security_cli):
    """
    Creates service account with `hello-world` name and yields the name.
    """
    name = 'kafka'
    sdk_security.create_service_account(
        service_account_name=name, secret_name=name)
    # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
    sdk_cmd.run_cli(
        "security org groups add_user superusers {name}".format(name=name))
    yield name
    sdk_security.delete_service_account(
        service_account_name=name, secret_name=name)


@pytest.fixture(scope='module')
def kafka_service_tls(service_account):
    sdk_install.install(
        PACKAGE_NAME,
        DEFAULT_BROKER_COUNT,
        service_name=service_account,
        additional_options={
            "service": {
                "secret_name": service_account,
                "principal": service_account,
                "tls": True,
            }
        }
    )

    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)

    # Wait for service health check to pass
    shakedown.service_healthy(PACKAGE_NAME)

    yield service_account

    sdk_install.uninstall(PACKAGE_NAME)


@pytest.mark.tls
@pytest.mark.smoke
@pytest.mark.sanity
@sdk_utils.dcos_1_10_or_higher
def test_tls_endpoints(kafka_service_tls):
    endpoints = sdk_networks.get_and_test_endpoints("", PACKAGE_NAME, 3)
    assert BROKER_TLS_ENDPOINT in endpoints

    # Test that broker-tls endpoint is available
    endpoint_tls = service_cli(
        'endpoints {name}'.format(name=BROKER_TLS_ENDPOINT)
    )
    assert len(endpoint_tls['dns']) == DEFAULT_BROKER_COUNT


@pytest.mark.tls
@pytest.mark.smoke
@pytest.mark.sanity
@sdk_utils.dcos_1_10_or_higher
def test_producer_over_tls(kafka_service_tls):
    service_cli('topic create {}'.format(DEFAULT_TOPIC_NAME))

    topic_info = service_cli('topic describe {}'.format(DEFAULT_TOPIC_NAME))
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT

    # Warm up TLS connections
    write_info = service_cli('topic producer_test_tls {} {}'.format(DEFAULT_TOPIC_NAME, 10))

    num_messages = 10
    write_info = service_cli('topic producer_test_tls {} {}'.format(DEFAULT_TOPIC_NAME, num_messages))
    assert len(write_info) == 1
    assert write_info['message'].startswith('Output: {} records sent'.format(num_messages))
