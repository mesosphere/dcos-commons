import json

import pytest
import shakedown

import sdk_cmd
import sdk_install
import sdk_networks
import sdk_plan
import sdk_security
import sdk_utils

from tests import config

from tests.test_utils import (
    service_cli
)

# Name of the broker TLS vip
BROKER_TLS_ENDPOINT = 'broker-tls'


@pytest.fixture(scope='module')
def service_account():
    """
    Creates service account with `hello-world` name and yields the name.
    """
    name = 'kafka'
    sdk_security.create_service_account(
        service_account_name=name, service_account_secret=name)
    # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
    sdk_cmd.run_cli(
        "security org groups add_user superusers {name}".format(name=name))
    yield name
    sdk_security.delete_service_account(
        service_account_name=name, service_account_secret=name)


@pytest.fixture(scope='module')
def kafka_service_tls(service_account):
    sdk_install.install(
        config.PACKAGE_NAME,
        config.DEFAULT_BROKER_COUNT,
        service_name=service_account,
        additional_options={
            "service": {
                "service_account": service_account,
                "service_account_secret": service_account,
                # Legacy values
                "principal": service_account,
                "secret_name": service_account,
                "tls": True
            }
        }
    )

    sdk_plan.wait_for_completed_deployment(config.PACKAGE_NAME)

    # Wait for service health check to pass
    shakedown.service_healthy(config.PACKAGE_NAME)

    yield service_account

    sdk_install.uninstall(config.PACKAGE_NAME)


@pytest.mark.tls
@pytest.mark.smoke
@pytest.mark.sanity
@sdk_utils.dcos_1_10_or_higher
def test_tls_endpoints(kafka_service_tls):
    endpoints = sdk_networks.get_and_test_endpoints("", config.PACKAGE_NAME, 2)
    assert BROKER_TLS_ENDPOINT in endpoints

    # Test that broker-tls endpoint is available
    endpoint_tls = service_cli(
        'endpoints {name}'.format(name=BROKER_TLS_ENDPOINT)
    )
    assert len(endpoint_tls['dns']) == config.DEFAULT_BROKER_COUNT


@pytest.mark.tls
@pytest.mark.smoke
@pytest.mark.sanity
@sdk_utils.dcos_1_10_or_higher
def test_producer_over_tls(kafka_service_tls):
    service_cli('topic create {}'.format(config.DEFAULT_TOPIC_NAME))

    topic_info = service_cli('topic describe {}'.format(config.DEFAULT_TOPIC_NAME))
    assert len(topic_info['partitions']) == config.DEFAULT_PARTITION_COUNT

    # Warm up TLS connections
    write_info = service_cli('topic producer_test_tls {} {}'.format(config.DEFAULT_TOPIC_NAME, 10))

    num_messages = 10
    write_info = service_cli('topic producer_test_tls {} {}'.format(config.DEFAULT_TOPIC_NAME, num_messages))
    assert len(write_info) == 1
    assert write_info['message'].startswith('Output: {} records sent'.format(num_messages))
