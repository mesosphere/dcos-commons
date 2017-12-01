import logging
import pytest
import subprocess
import uuid

import sdk_auth
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_tasks
import sdk_utils
import sdk_security

from tests import config

log = logging.getLogger(__name__)

@pytest.fixture(scope='module', autouse=True)
def service_account(configure_security):
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


@pytest.fixture(scope='module', autouse=True)
def configure_package(service_account):
    try:
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
                        },
                        "ssl_auth": {
                            "enable_authentication": True
                        }
                    }
                }
            })

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

@pytest.fixture(scope='module', autouse=True)
def kafka_client(configure_package):
    try:
        client_id = "kafka-client"
        client = {
            "id": client_id,
            "mem": 512,
            "user": "nobody",
            "container": {
                "type": "MESOS",
                "docker": {
                    "image": "elezar/kafka-client:latest",
                    "forcePullImage": True
                }
            },
            "networks": [
                {
                    "mode": "host"
                }
            ],
            "env": {
                "JVM_MaxHeapSize": "512",
                "KAFKA_CLIENT_MODE": "test",
                "KAFKA_SERVICE_NAME": config.SERVICE_NAME
            }
        }

        sdk_marathon.install_app(client)
        yield client["id"]

    finally:
        sdk_marathon.destroy_app(client_id)


@pytest.mark.dcos_min_version('1.10')
@pytest.mark.sanity
def test_client_can_read_and_write(kafka_client):
    brokers = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "endpoint broker", json=True)
    broker_dns = list(map(lambda x: x.split(':')[0], brokers["dns"]))   

    log.info("Running bootstrap to wait for DNS resolution")
    bootstrap_cmd = ['/opt/bootstrap', '-resolve-hosts', ','.join(broker_dns), '-verbose']
    bootstrap_output = sdk_tasks.task_exec(kafka_client, ' '.join(bootstrap_cmd))
    log.info(bootstrap_output)

    log.info("Generating certificate")
    token = sdk_cmd.run_cli("config show core.dcos_acs_token")
    output = sdk_tasks.task_exec(kafka_client,
        'openssl req -nodes -newkey rsa:2048 -keyout private.key -out CSR.csr \
        -subj "/C=US/ST=CA/L=SF/O=Mesosphere/OU=Mesosphere/CN=kafka-tester"')
    log.info(output)

    
    pass
