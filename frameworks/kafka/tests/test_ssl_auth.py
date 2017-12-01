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

from tests import config

log = logging.getLogger(__name__)

@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options={
                "service": {
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
        install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

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
                },
                "volumes": [
                    {
                        "containerPath": "/tmp/kafkaconfig/kafka-client.keytab",
                        "secret": "kafka_keytab"
                    }
                ]
            },
            "secrets": {
                "kafka_keytab": {
                    "source": kerberos.get_keytab_path(),

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
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_client_can_read_and_write(kafka_client):
    brokers = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "endpoint broker", json=True)
    broker_dns = list(map(lambda x: x.split(':')[0], brokers["dns"]))   

    log.info("Running bootstrap to wait for DNS resolution")
    bootstrap_cmd = ['/opt/bootstrap', '-resolve-hosts', ','.join(broker_dns), '-verbose']
    bootstrap_output = sdk_tasks.task_exec(kafka_client, ' '.join(bootstrap_cmd))
    log.info(bootstrap_output)
    
    pass
