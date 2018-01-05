import logging
import uuid
import pytest
import retrying

import sdk_auth
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_security
import sdk_tasks
import sdk_utils

from tests import auth
from tests import config
from tests import test_utils


log = logging.getLogger(__name__)
LOG = log

pytestmark = pytest.mark.skipif(sdk_utils.is_open_dcos(),
                                reason='Feature only supported in DC/OS EE')


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

    clients = [
        "client",
        "authorized",
        "unauthorized",
        "super"
    ]
    for c in clients:
        principals.append("{client}@{realm}".format(client=c, realm=sdk_auth.REALM))

    yield principals


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_security, kafka_principals):
    try:
        principals = []
        principals.extend(kafka_principals)

        kerberos_env = sdk_auth.KerberosEnvironment()
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope='module', autouse=True)
def kafka_server(kerberos, service_account):
    """
    A pytest fixture that installs a Kerberized kafka service.

    On teardown, the service is uninstalled.
    """
    service_kerberos_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "service_account": service_account,
            "service_account_secret": service_account,
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc": {
                        "hostname": kerberos.get_host(),
                        "port": int(kerberos.get_port())
                    },
                    "keytab_secret": kerberos.get_keytab_path(),
                },
                "transport_encryption": {
                    "enabled": True
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
def kafka_client(kerberos, kafka_server):

    brokers = sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "endpoint broker-tls", json=True)["dns"]

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
                "KAFKA_TOPIC": "securetest",
                "KAFKA_BROKER_LIST": ",".join(brokers)
            }
        }

        sdk_marathon.install_app(client)

        auth.create_tls_artifacts(
            cn="client",
            task=client_id)

        yield {**client, **{"brokers": list(map(lambda x: x.split(':')[0], brokers))}}

    finally:
        sdk_marathon.destroy_app(client_id)


@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_client_can_read_and_write(kafka_client, kafka_server):
    client_id = kafka_client["id"]

    auth.wait_for_brokers(kafka_client["id"], kafka_client["brokers"])

    topic_name = "authn.test"
    sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"],
                    "topic create {}".format(topic_name),
                    json=True)

    test_utils.wait_for_topic(kafka_server["package_name"], kafka_server["service"]["name"], topic_name)

    message = str(uuid.uuid4())

    assert write_to_topic("client", client_id, topic_name, message)

    assert message in read_from_topic("client", client_id, topic_name, 1)


def write_client_properties(cn: str, task: str) -> str:
    sdk_tasks.task_exec(task, """bash -c \"cat >{cn}-client.properties << EOL
security.protocol=SASL_SSL
sasl.mechanism=GSSAPI
sasl.kerberos.service.name=kafka
ssl.truststore.location = {cn}_truststore.jks
ssl.truststore.password = changeit
ssl.keystore.location = {cn}_keystore.jks
ssl.keystore.password = changeit
EOL\"""".format(cn=cn))

    return "{}-client.properties".format(cn)


def write_to_topic(cn: str, task: str, topic: str, message: str, cmd: str=None) -> str:
    if not cmd:
        env_str = auth.setup_env(cn, task)
        client_properties = write_client_properties(cn, task)

        write_cmd = "bash -c \"{} && echo {} | kafka-console-producer \
            --topic {} \
            --producer.config {} \
            --broker-list \$KAFKA_BROKER_LIST\"".format(env_str,
                                                        message,
                                                        topic,
                                                        client_properties)
    else:
        write_cmd = cmd

    def write_failed(output) -> bool:
        LOG.info("Checking write output: %s", output)
        rc = output[0]
        stderr = output[2]

        if rc:
            LOG.error("Write failed with non-zero return code")
            return True
        if "UNKNOWN_TOPIC_OR_PARTITION" in stderr:
            LOG.error("Write failed due to stderr: UNKNOWN_TOPIC_OR_PARTITION")
            return True
        if "LEADER_NOT_AVAILABLE" in stderr and "ERROR Error when sending message" in stderr:
            LOG.error("Write failed due to stderr: LEADER_NOT_AVAILABLE")
            return True

        LOG.info("Output check passed")

        return False

    @retrying.retry(wait_exponential_multiplier=1000,
                    wait_exponential_max=60 * 1000,
                    retry_on_result=write_failed)
    def write_wrapper():
        LOG.info("Running: %s", write_cmd)
        rc, stdout, stderr = sdk_tasks.task_exec(task, write_cmd)
        LOG.info("rc=%s\nstdout=%s\nstderr=%s\n", rc, stdout, stderr)

        return rc, stdout, stderr

    rc, stdout, stderr = write_wrapper()

    rc_success = rc is 0
    stdout_success = ">>" in stdout
    stderr_success = not auth.is_not_authorized(stderr)

    return rc_success and stdout_success and stderr_success


def read_from_topic(cn: str, task: str, topic: str, messages: int, cmd: str=None) -> str:
    if not cmd:
        env_str = auth.setup_env(cn, task)
        client_properties = write_client_properties(cn, task)
        timeout_ms = 60000
        read_cmd = "bash -c \"{} && kafka-console-consumer \
            --topic {} \
            --consumer.config {} \
            --bootstrap-server \$KAFKA_BROKER_LIST \
            --from-beginning --max-messages {} \
            --timeout-ms {} \
            \"".format(env_str, topic, client_properties, messages, timeout_ms)
    else:
        read_cmd = cmd

    def read_failed(output) -> bool:
        LOG.info("Checking read output: %s", output)
        rc = output[0]
        stderr = output[2]

        if rc:
            LOG.error("Read failed with non-zero return code")
            return True
        if "kafka.consumer.ConsumerTimeoutException" in stderr:
            return True

        LOG.info("Output check passed")

        return False

    @retrying.retry(wait_exponential_multiplier=1000,
                    wait_exponential_max=60 * 1000,
                    retry_on_result=read_failed)
    def read_wrapper():
        LOG.info("Running: %s", read_cmd)
        rc, stdout, stderr = sdk_tasks.task_exec(task, read_cmd)
        LOG.info("rc=%s\nstdout=%s\nstderr=%s\n", rc, stdout, stderr)

        return rc, stdout, stderr

    output = read_wrapper()

    assert output[0] is 0
    return " ".join(str(o) for o in output)
