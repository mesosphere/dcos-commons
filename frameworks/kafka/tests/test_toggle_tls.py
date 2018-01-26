import logging
import json
import tempfile
import pytest
import uuid

import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_security
import sdk_utils

from security import transport_encryption

from tests import auth
from tests import config
from tests import test_utils


log = logging.getLogger(__name__)


pytestmark = [
    pytest.mark.skipif(sdk_utils.is_open_dcos(), reason="TLS tests required DC/OS EE"),
]


MESSAGES = []


@pytest.fixture(scope='module')
def service_account(configure_security):
    """
    Creates service account and yields the name.
    """
    try:
        name = config.SERVICE_NAME
        secret = "{}-secret".format(name)
        sdk_security.create_service_account(
            service_account_name=name, service_account_secret=secret)
        # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
        sdk_cmd.run_cli(
            "security org groups add_user superusers {name}".format(name=name))
        yield {"name": name, "secret": secret}
    finally:
        sdk_security.delete_service_account(
            service_account_name=name, service_account_secret=secret)


@pytest.fixture(scope='module', autouse=True)
def kafka_server(service_account):
    """
    A pytest fixture that installs a non-kerberized kafka service.

    On teardown, the service is uninstalled.
    """
    service_tls_options = {
        "service": {
            "name": config.SERVICE_NAME,
            # Note that since we wish to toggle TLS which *REQUIRES* a service account,
            # we need to install Kafka with a service account to start with.
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_tls_options,
            timeout_seconds=30 * 60)

        yield {**service_tls_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope='module', autouse=True)
def kafka_client(kafka_server):

    brokers = sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "endpoint broker", json=True)["dns"]

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
                "KAFKA_BROKER_LIST": ",".join(brokers),
                "KAFKA_OPTS": ""
            }
        }

        sdk_marathon.install_app(client)

        # Create a TLS certificate for the TLS tests
        transport_encryption.create_tls_artifacts(
            cn="kafka-tester",
            task=client_id)

        yield {
                **client,
                **{
                    "brokers": list(map(lambda x: x.split(':')[0], brokers)),
                    "tls-id": "kafka-tester",
                }
            }

    finally:
        sdk_marathon.destroy_app(client_id)


def test_default_installation(kafka_client, kafka_server):
    assert check_brokers_are_unchanged(kafka_client, kafka_server, True, False)
    write_success, read_success = client_can_read_and_write("default",
                                                            kafka_client, kafka_server, None)

    assert write_success, "Write failed"
    assert read_success, "Read failed: MESSAGES={} read_success={}".format(MESSAGES, read_success)


def test_enable_tls_allow_plaintext(kafka_client, kafka_server, service_account):
    tls_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
            "security": {
                "transport_encryption": {
                    "enabled": True,
                    "allow_plaintext": True
                }
            }
        }
    }

    with tempfile.NamedTemporaryFile("w", suffix=".kerberos.json") as f:
        tls_options_path = f.name

        log.info("Writing kerberos options to %s", tls_options_path)
        json.dump(tls_options, f)
        f.flush()

        cmd = ["update", "start", "--options={}".format(tls_options_path)]
        sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"], " ".join(cmd))

        # An update plan is a deploy plan
        sdk_plan.wait_for_kicked_off_deployment(kafka_server["service"]["name"])
        sdk_plan.wait_for_completed_deployment(kafka_server["service"]["name"])

    assert check_brokers_are_unchanged(kafka_client, kafka_server, True, False), "non-TLS endpoint expected"
    assert check_brokers_are_unchanged(kafka_client, kafka_server, False, True), "TLS enpoint expected"

    write_success, read_success = client_can_read_and_write("default",
                                                            kafka_client, kafka_server, False)

    assert write_success, "Write failed"
    assert read_success, "Read failed: MESSAGES={} read_success={}".format(MESSAGES, read_success)

    write_success, read_success = client_can_read_and_write(kafka_client["tls-id"],
                                                            kafka_client, kafka_server, True)

    assert write_success, "Write failed"
    assert read_success, "Read failed: MESSAGES={} read_success={}".format(MESSAGES, read_success)


def test_enable_tls_disable_plaintext(kafka_client, kafka_server, service_account):
    tls_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
            "security": {
                "transport_encryption": {
                    "enabled": True,
                    "allow_plaintext": False
                }
            }
        }
    }

    with tempfile.NamedTemporaryFile("w", suffix=".kerberos.json") as f:
        tls_options_path = f.name

        log.info("Writing kerberos options to %s", tls_options_path)
        json.dump(tls_options, f)
        f.flush()

        cmd = ["update", "start", "--options={}".format(tls_options_path)]
        sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"], " ".join(cmd))

        # An update plan is a deploy plan
        sdk_plan.wait_for_kicked_off_deployment(kafka_server["service"]["name"])
        sdk_plan.wait_for_completed_deployment(kafka_server["service"]["name"])

    assert not check_brokers_are_unchanged(kafka_client, kafka_server, True, False), "non-TLS endpoint not expected"
    assert check_brokers_are_unchanged(kafka_client, kafka_server, False, True), "TLS enpoint expected"

    write_success, read_success = client_can_read_and_write(kafka_client["tls-id"],
                                                            kafka_client, kafka_server, True)

    assert write_success, "Write failed"
    assert read_success, "Read failed: MESSAGES={} read_success={}".format(MESSAGES, read_success)


def test_enable_tls_reenable_plaintext(kafka_client, kafka_server, service_account):
    tls_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
            "security": {
                "transport_encryption": {
                    "enabled": True,
                    "allow_plaintext": True
                }
            }
        }
    }

    with tempfile.NamedTemporaryFile("w", suffix=".kerberos.json") as f:
        tls_options_path = f.name

        log.info("Writing kerberos options to %s", tls_options_path)
        json.dump(tls_options, f)
        f.flush()

        cmd = ["update", "start", "--options={}".format(tls_options_path)]
        sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"], " ".join(cmd))

        # An update plan is a deploy plan
        sdk_plan.wait_for_kicked_off_deployment(kafka_server["service"]["name"])
        sdk_plan.wait_for_completed_deployment(kafka_server["service"]["name"])

    assert check_brokers_are_unchanged(kafka_client, kafka_server, True, False), "non-TLS endpoint expected"
    assert check_brokers_are_unchanged(kafka_client, kafka_server, False, True), "TLS enpoint expected"

    write_success, read_success = client_can_read_and_write("default",
                                                            kafka_client, kafka_server, False)

    assert write_success, "Write failed"
    assert read_success, "Read failed: MESSAGES={} read_success={}".format(MESSAGES, read_success)

    write_success, read_success = client_can_read_and_write(kafka_client["tls-id"],
                                                            kafka_client, kafka_server, True)

    assert write_success, "Write failed"
    assert read_success, "Read failed: MESSAGES={} read_success={}".format(MESSAGES, read_success)


def test_disable_tls(kafka_client, kafka_server, service_account):
    tls_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
            "security": {
                "transport_encryption": {
                    "enabled": False
                }
            }
        }
    }

    with tempfile.NamedTemporaryFile("w", suffix=".kerberos.json") as f:
        tls_options_path = f.name

        log.info("Writing kerberos options to %s", tls_options_path)
        json.dump(tls_options, f)
        f.flush()

        cmd = ["update", "start", "--options={}".format(tls_options_path)]
        sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"], " ".join(cmd))

        # An update plan is a deploy plan
        sdk_plan.wait_for_kicked_off_deployment(kafka_server["service"]["name"])
        sdk_plan.wait_for_completed_deployment(kafka_server["service"]["name"])

    assert check_brokers_are_unchanged(kafka_client, kafka_server, True, False), "non-TLS endpoint expected"
    assert not check_brokers_are_unchanged(kafka_client, kafka_server, False, True), "TLS enpoint not expected"

    write_success, read_success = client_can_read_and_write("default",
                                                            kafka_client, kafka_server, False)

    assert write_success, "Write failed"
    assert read_success, "Read failed: MESSAGES={} read_success={}".format(MESSAGES, read_success)


def check_brokers_are_unchanged(kafka_client: dict, kafka_server: dict,
                                match_ports: bool=True, is_tls: bool=False) -> bool:

    endpoints = sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "endpoint", json=True)

    if is_tls:
        endpoint_name = "broker-tls"
    else:
        endpoint_name = "broker"

    if endpoint_name not in endpoints:
        log.error("Expecting endpoint %s. Found %s", endpoint_name, endpoints)
        return False

    client_brokers = kafka_client["env"]["KAFKA_BROKER_LIST"].split(",")

    brokers = sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "endpoint {}".format(endpoint_name), json=True)["dns"]

    if match_ports:
        return set(client_brokers) == set(brokers)

    log.info("Only checking hostnames")

    def get_hostnames(broker_list):
        return map(lambda b: b.split(":")[0], broker_list)

    return set(get_hostnames(brokers)) == set(get_hostnames(client_brokers))


def client_can_read_and_write(test_id: str,
                              kafka_client: dict, kafka_server: dict, is_tls=bool) -> tuple:
    client_id = kafka_client["id"]

    if is_tls:
        endpoint_name = "broker-tls"
    else:
        endpoint_name = "broker"

    brokers_list = sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "endpoint {}".format(endpoint_name), json=True)["dns"]
    brokers = ",".join(brokers_list)

    auth.wait_for_brokers(kafka_client["id"], kafka_client["brokers"])

    topic_name = kafka_client["env"]["KAFKA_TOPIC"]
    sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"],
                    "topic create {}".format(topic_name),
                    json=True)

    test_utils.wait_for_topic(kafka_server["package_name"], kafka_server["service"]["name"], topic_name)

    message = str(uuid.uuid4())

    write_success = write_to_topic(test_id, client_id, topic_name, message, brokers, is_tls)
    if write_success:
        MESSAGES.append(message)

    read_messages = read_from_topic(test_id, client_id, topic_name, len(MESSAGES), brokers, is_tls)

    read_success = map(lambda m: m in read_messages, MESSAGES)

    return write_success, read_success


def get_settings(cn: str, task: str, tls: bool) -> tuple:

    if not tls:
        return [], None

    properties = auth.get_ssl_client_properties(cn, False)
    environment = None

    return properties, environment


def write_to_topic(cn: str, task: str, topic: str, message: str, brokers: str, tls: bool) -> bool:

    properties, environment = get_settings(cn, task, tls)
    return auth.write_to_topic(cn, task, topic, message, properties, environment, brokers)


def read_from_topic(cn: str, task: str, topic: str, messages: int, brokers: str, tls: bool) -> str:

    properties, environment = get_settings(cn, task, tls)
    return auth.read_from_topic(cn, task, topic, messages, properties, environment, brokers)
