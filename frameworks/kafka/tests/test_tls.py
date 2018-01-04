import pytest
import shakedown

import sdk_cmd
import sdk_install
import sdk_networks
import sdk_plan
import sdk_security
import sdk_utils

from tests import config

# Name of the broker TLS vip
BROKER_TLS_ENDPOINT = 'broker-tls'


@pytest.fixture(scope='module')
def service_account():
    """
    Creates service account and yields the name.
    """
    name = config.SERVICE_NAME
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
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    config.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_BROKER_COUNT,
        additional_options={
            "service": {
                "service_account": service_account,
                "service_account_secret": service_account,
                "security": {
                    "transport_encryption": {
                        "enabled": True
                    }
                }
            }
        }
    )

    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    # Wait for service health check to pass
    shakedown.service_healthy(config.SERVICE_NAME)

    yield service_account

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.tls
@pytest.mark.smoke
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
@pytest.mark.dcos_min_version('1.10')
def test_tls_endpoints(kafka_service_tls):
    endpoints = sdk_networks.get_and_test_endpoints(config.PACKAGE_NAME, config.SERVICE_NAME, "", 2)
    assert BROKER_TLS_ENDPOINT in endpoints

    # Test that broker-tls endpoint is available
    endpoint_tls = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'endpoints {name}'.format(name=BROKER_TLS_ENDPOINT), json=True)
    assert len(endpoint_tls['dns']) == config.DEFAULT_BROKER_COUNT


@pytest.mark.tls
@pytest.mark.smoke
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
@pytest.mark.dcos_min_version('1.10')
def test_producer_over_tls(kafka_service_tls):
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'topic create {}'.format(config.DEFAULT_TOPIC_NAME))

    topic_info = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'topic describe {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    assert len(topic_info['partitions']) == config.DEFAULT_PARTITION_COUNT

    # Write twice: Warm up TLS connections
    num_messages = 10
    write_info = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'topic producer_test_tls {} {}'.format(config.DEFAULT_TOPIC_NAME, num_messages), json=True)

    write_info = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'topic producer_test_tls {} {}'.format(config.DEFAULT_TOPIC_NAME, num_messages), json=True)
    assert len(write_info) == 1
    assert write_info['message'].startswith('Output: {} records sent'.format(num_messages))
