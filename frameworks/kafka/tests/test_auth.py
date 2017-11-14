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

from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_universe):
    service_name = "secure-kafka"
    try:
        fqdn = "{service_name}.{host_suffix}".format(service_name=service_name,
                                                     host_suffix=sdk_hosts.AUTOIP_HOST_SUFFIX)

        brokers = [
            "kafka-0-broker",
            "kafka-1-broker",
            "kafka-2-broker",
        ]

        principals = []
        for b in brokers:
            principals.append("kafka/{instance}.{domain}@{realm}".format(
                              instance=b,
                              domain=fqdn,
                              realm=sdk_auth.REALM))

        principals.append("client@{realm}".format(realm=sdk_auth.REALM))

        kerberos_env = sdk_auth.KerberosEnvironment()
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        service_kerberos_options = {
            "service": {
                "name": service_name,
                "security": {
                    "kerberos": {
                        "enabled": True,
                        "kdc_host_name": kerberos_env.get_host(),
                        "kdc_host_port": int(kerberos_env.get_port()),
                        "keytab_secret": kerberos_env.get_keytab_path(),
                    }
                }
            }
        }

        sdk_install.uninstall(config.PACKAGE_NAME, service_name)
        sdk_install.install(
            config.PACKAGE_NAME,
            service_name,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_kerberos_options,
            timeout_seconds=30 * 60)

        yield kerberos_env

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, service_name)
        kerberos_env.cleanup()


@pytest.fixture(scope='module', autouse=True)
def kafka_client(kerberos):
    try:
        client_id = "kafka-client"
        client = {
            "id": client_id,
            "mem": 512,
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
                "KAFKA_CLIENT_MODE": "test"
            }
        }

        sdk_marathon.install_app(client)
        yield client["id"]

    finally:
        sdk_marathon.destroy_app(client_id)


@pytest.mark.sanity
def test_client_can_read_and_write(kafka_client):

    topics = sdk_cmd.svc_cli(config.PACKAGE_NAME, "secure-kafka", "topic create securetest", json=True)
    log.info("Created topic: %s", topics)

    message = uuid.uuid4()
    producer_cmd = ['/tmp/kafkaconfig/start.sh', 'producer', str(message)]

    for i in range(2):
        log.info("Running(%s) %s", i, producer_cmd)

        producer_output = sdk_tasks.task_exec(kafka_client, ' '.join(producer_cmd))
        log.info("Producer output(%s): %s", i, producer_output)

    assert "Sent message: '{message}'".format(message=str(message)) in ' '.join(str(p) for p in producer_output)

    consumer_cmd = ['/tmp/kafkaconfig/start.sh', 'consumer', 'single']
    log.info("Running %s", consumer_cmd)
    consumer_output = sdk_tasks.task_exec(kafka_client,  ' '.join(consumer_cmd))
    log.info("Consumer output: %s", consumer_output)

    assert str(message) in ' '.join(str(c) for c in consumer_output)
