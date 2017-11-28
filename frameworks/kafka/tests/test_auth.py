import logging
import pytest
import subprocess
import uuid

import sdk_auth
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_repository
import sdk_tasks
import sdk_utils

from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def kafka_principals():
    fqdn = "{service_name}.{host_suffix}".format(service_name=config.SERVICE_NAME,
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

    yield principals


@pytest.fixture(scope='module', autouse=True)
def zookeeper_principals():
    zk_fqdn = "{service_name}.{host_suffix}".format(service_name="kafka-zookeeper",
                                                    host_suffix=sdk_hosts.AUTOIP_HOST_SUFFIX)

    zk_ensemble = [
        "zookeeper-0-server",
        "zookeeper-1-server",
        "zookeeper-2-server",
    ]

    principals = []
    for b in zk_ensemble:
        principals.append("zookeeper/{instance}.{domain}@{realm}".format(
                            instance=b,
                            domain=zk_fqdn,
                            realm=sdk_auth.REALM))

    yield principals


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_security, kafka_principals, zookeeper_principals):
    try:
        fqdn = "{service_name}.{host_suffix}".format(service_name=config.SERVICE_NAME,
                                                     host_suffix=sdk_hosts.AUTOIP_HOST_SUFFIX)

        principals = []
        principals.extend(kafka_principals)
        principals.extend(zookeeper_principals)

        kerberos_env = sdk_auth.KerberosEnvironment()
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope='module')
def zookeeper_server(kerberos):
    service_kerberos_options = {
        "service": {
            "name": "kafka-zookeeper",
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc_host_name": kerberos.get_host(),
                    "kdc_host_port": int(kerberos.get_port()),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            }
        }
    }

    # TODO: Remove once kafka-zookeeper is available in the universe
    zookeeper_stub = "https://universe-converter.mesosphere.com/transform?url=https://infinity-artifacts-ci.s3.amazonaws.com/autodelete7d/kafka-zookeeper/20171128-131117-nfLShZUGg0mS9H2y/stub-universe-kafka-zookeeper.json"
    stub_urls = sdk_repository.add_stub_universe_urls([kafka_stub, ])

    try:
        sdk_install.uninstall("beta-kafka-zookeeper", "kafka-zookeeper")
        sdk_install.install(
            "beta-kafka-zookeeper",
            "kafka-zookeeper",
            6,
            additional_options=service_kerberos_options,
            timeout_seconds=30 * 60)

        yield {"package_name": "beta-kafka-zookeeper", "service_name": "kafka-zookeeper"}

    finally:
        sdk_install.uninstall("beta-kafka-zookeeper", "kafka-zookeeper")
        sdk_repository.remove_universe_repos(stub_urls)


@pytest.fixture(scope='module', autouse=True)
def kafka_server(kerberos):
    service_kerberos_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc_host_name": kerberos.get_host(),
                    "kdc_host_port": int(kerberos.get_port()),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            }
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_kerberos_options,
            timeout_seconds=30 * 60)

        yield {**service_kerberos_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope='module', autouse=True)
def kafka_server_krb5_zookeeper(kerberos, zookeeper_server):
    zookeeper_dns = sdk_cmd.svc_cli(
        zookeeper_server["package_name"], zookeeper_server["service_name"], "endpoint clientport", json=True)["dns"]

    service_kerberos_options = {
        "service": {
            "name": "kafka-with-krb5-zookeeper",
            "security": {
                "kerberos": {
                    "enabled": True,
                    "enabled_for_zookeeper": True,
                    "kdc_host_name": kerberos.get_host(),
                    "kdc_host_port": int(kerberos.get_port()),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            }
        },
        "kafka": {
            "kafka_zookeeper_uri": ",".join(zookeeper_dns)
        }
    }

    try:
        sdk_install.uninstall("beta-kafka", "kafka")
        sdk_install.install(
            "beta-kafka",
            "kafka",
            3,
            additional_options=service_kerberos_options,
            timeout_seconds=30 * 60)

        yield {**service_kerberos_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, kafka_kerberos_options["service"]["name"])


@pytest.fixture(scope='module', autouse=True)
def kafka_client(kerberos, kafka_server):

    brokers = sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "endpoint broker", json=True)["dns"]

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
                "KAFKA_BROKER_LIST": ",".join(brokers)
            }
        }

        sdk_marathon.install_app(client)
        yield {**client, **{"brokers": list(map(lambda x: x.split(':')[0], brokers))}}

    finally:
        sdk_marathon.destroy_app(client_id)


@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_client_can_read_and_write(kafka_client):

    wait_for_brokers(kafka_client["id"], kafka_client["brokers"])
    send_and_recieve_message(kafka_client["id"])


@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_client_can_read_and_write_from_kerberized_zookeeper(kerberos, kafka_server_krb5_zookeeper):
    client = kafka_client(kerberos, kafka_server_krb5_zookeeper)

    wait_for_brokers(client["id"], client["brokers"])
    send_and_recieve_message(client["id"])


def wait_for_brokers(client: str, brokers: list):
    log.info("Running bootstrap to wait for DNS resolution")
    bootstrap_cmd = ['/opt/bootstrap', '-resolve-hosts', ','.join(brokers), '-verbose']
    bootstrap_output = sdk_tasks.task_exec(kafka_client, ' '.join(bootstrap_cmd))
    log.info(bootstrap_output)


def send_and_recieve_message(client: str):
    message = uuid.uuid4()
    producer_cmd = ['/tmp/kafkaconfig/start.sh', 'producer', str(message)]

    for i in range(2):
        log.info("Running(%s) %s", i, producer_cmd)
        producer_output = sdk_tasks.task_exec(client, ' '.join(producer_cmd))
        log.info("Producer output(%s): %s", i, producer_output)

    assert "Sent message: '{message}'".format(message=str(message)) in ' '.join(str(p) for p in producer_output)

    consumer_cmd = ['/tmp/kafkaconfig/start.sh', 'consumer', 'single']
    log.info("Running %s", consumer_cmd)
    consumer_output = sdk_tasks.task_exec(client,  ' '.join(consumer_cmd))
    log.info("Consumer output: %s", consumer_output)

    assert str(message) in ' '.join(str(c) for c in consumer_output)
