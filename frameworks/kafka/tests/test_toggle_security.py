import logging
import json
import tempfile
import uuid
import pytest

import sdk_auth
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_utils

from security import transport_encryption

from tests import auth
from tests import config
from tests import test_utils


log = logging.getLogger(__name__)


pytestmark = [
    pytest.mark.skip(reason="INFINTY-INFINITY-3367: Address issues in Kafka security toggle"),
    pytest.mark.skipif(sdk_utils.is_open_dcos(),
                       reason="Security tests require DC/OS EE"),
    pytest.mark.skipif(sdk_utils.dcos_version_less_than("1.10"),
                       reason="Security tests require DC/OS 1.10+"),
]


MESSAGES = []


@pytest.fixture(scope='module', autouse=True)
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        service_account_info = transport_encryption.setup_service_account(config.SERVICE_NAME)

        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(service_account_info)


@pytest.fixture(scope='module', autouse=True)
def kerberos():
    """
    A pytest fixture that installs and configures a KDC used for testing.

    On teardown, the KDC application is removed.
    """
    try:
        kerberos_env = sdk_auth.KerberosEnvironment()

        principals = auth.get_service_principals(config.SERVICE_NAME,
                                                 kerberos_env.get_realm())
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope='module', autouse=True)
def kafka_server(service_account):
    """
    A pytest fixture that installs a non-kerberized kafka service.

    On teardown, the service is uninstalled.
    """
    service_options = {
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
            additional_options=service_options,
            timeout_seconds=30 * 60)

        yield {**service_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope='module', autouse=True)
def kafka_client(kerberos):
    """
    A pytest fixture to install a Kafka client as a Marathon application.
    This client is capable of both Kerberos and TLS communication.

    On teardown, the client is uninstalled.
    """
    try:
        client_id = "kafka-client"
        client = {
            "id": client_id,
            "mem": 512,
            "container": {
                "type": "MESOS",
                "docker": {
                    "image": "elezar/kafka-client:4b9c060",
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
                "KAFKA_BROKER_LIST": ""
            }
        }

        sdk_marathon.install_app(client)

        # Create a TLS certificate for the TLS tests
        transport_encryption.create_tls_artifacts(
            cn="client",
            marathon_task=client_id)

        yield {
            **client,
            **{
                "tls-id": "client",
            }
        }

    finally:
        sdk_marathon.destroy_app(client_id)


@pytest.mark.incremental
def test_initial_kerberos_off_tls_off_plaintext_off(kafka_client, kafka_server):
    """
    Check the default no-security state is sane.
    """
    assert service_has_brokers(kafka_server, "broker", config.DEFAULT_BROKER_COUNT), "non-TLS enpoints expected"
    assert not service_has_brokers(kafka_server, "broker-tls"), "TLS enpoints not expected"

    write_success, read_successes = client_can_read_and_write("default", kafka_client, kafka_server, "broker")
    assert write_success, "Write failed"
    assert read_successes, "Read failed: MESSAGES={} read_successes={}".format(MESSAGES, read_successes)


@pytest.mark.incremental
def test_forward_kerberos_on_tls_off_plaintext_off(kafka_client, kafka_server, kerberos):
    update_options = {
        "service": {
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc": {
                        "hostname": kerberos.get_host(),
                        "port": int(kerberos.get_port())
                    },
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            }
        }
    }

    brokers = service_get_brokers(kafka_server, "broker")

    update_service(kafka_server["package_name"], kafka_server["service"]["name"], update_options)

    assert service_has_brokers(kafka_server, "broker", config.DEFAULT_BROKER_COUNT), "non-TLS enpoints expected"
    assert not service_has_brokers(kafka_server, "broker-tls"), "TLS enpoints not expected"

    updated_brokers = service_get_brokers(kafka_server, "broker")
    assert set(brokers) == set(updated_brokers), "Brokers should not change"

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker", kerberos)
    assert write_success, "Write failed"
    assert read_successes, "Read failed: MESSAGES={} read_successes={}".format(MESSAGES, read_successes)


@pytest.mark.incremental
def test_forward_kerberos_on_tls_on_plaintext_on(kafka_client, kafka_server, kerberos):
    update_options = {
        "service": {
            "security": {
                "transport_encryption": {
                    "enabled": True,
                    "allow_plaintext": True
                }
            }
        }
    }

    brokers = service_get_brokers(kafka_server, "broker")

    update_service(kafka_server["package_name"], kafka_server["service"]["name"], update_options)

    assert service_has_brokers(kafka_server, "broker", config.DEFAULT_BROKER_COUNT), "non-TLS enpoints expected"
    assert service_has_brokers(kafka_server, "broker-tls", config.DEFAULT_BROKER_COUNT), "TLS enpoints expected"

    updated_brokers = service_get_brokers(kafka_server, "broker")
    assert set(brokers) == set(updated_brokers), "Brokers should not change"

    tls_brokers = service_get_brokers(kafka_server, "broker-tls")

    assert set(_get_hostnames(brokers)) == set(_get_hostnames(tls_brokers)), "TLS and non-TLS broker " \
                                                                             "hostnames should match"

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker", kerberos)
    assert write_success, "Write failed"
    assert read_successes, "Read failed: MESSAGES={} read_successes={}".format(MESSAGES, read_successes)

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker-tls", kerberos)
    assert write_success, "Write failed (TLS)"
    assert read_successes, "Read failed (TLS): MESSAGES={} read_successes={}".format(MESSAGES, read_successes)


@pytest.mark.incremental
def test_forward_kerberos_on_tls_on_plaintext_off(kafka_client, kafka_server, kerberos):
    update_options = {
        "service": {
            "security": {
                "transport_encryption": {
                    "enabled": True,
                    "allow_plaintext": False
                }
            }
        }
    }

    brokers = service_get_brokers(kafka_server, "broker-tls")

    update_service(kafka_server["package_name"], kafka_server["service"]["name"], update_options)

    assert not service_has_brokers(kafka_server, "broker"), "non-TLS enpoints not expected"
    assert service_has_brokers(kafka_server, "broker-tls", config.DEFAULT_BROKER_COUNT), "TLS enpoints expected"

    updated_brokers = service_get_brokers(kafka_server, "broker-tls")
    assert set(brokers) == set(updated_brokers), "Brokers should not change"

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker-tls", kerberos)
    assert write_success, "Write failed (TLS)"
    assert read_successes, "Read failed (TLS): MESSAGES={} read_successes={}".format(MESSAGES, read_successes)


@pytest.mark.incremental
def test_forward_kerberos_off_tls_on_plaintext_off(kafka_client, kafka_server):
    update_options = {
        "service": {
            "security": {
                "kerberos": {
                    "enabled": False,
                }
            }
        }
    }

    brokers = service_get_brokers(kafka_server, "broker-tls")

    update_service(kafka_server["package_name"], kafka_server["service"]["name"], update_options)

    assert not service_has_brokers(kafka_server, "broker"), "non-TLS enpoints not expected"
    assert service_has_brokers(kafka_server, "broker-tls", config.DEFAULT_BROKER_COUNT), "TLS enpoints expected"

    updated_brokers = service_get_brokers(kafka_server, "broker-tls")
    assert set(brokers) == set(updated_brokers), "Brokers should not change"

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker-tls", None)
    assert write_success, "Write failed (TLS)"
    assert read_successes, "Read failed (TLS): MESSAGES={} read_successes={}".format(MESSAGES, read_successes)


@pytest.mark.incremental
def test_forward_kerberos_off_tls_on_plaintext_on(kafka_client, kafka_server):
    update_options = {
        "service": {
            "security": {
                "transport_encryption": {
                    "enabled": True,
                    "allow_plaintext": True
                }
            }
        }
    }

    brokers = service_get_brokers(kafka_server, "broker-tls")

    update_service(kafka_server["package_name"], kafka_server["service"]["name"], update_options)

    assert service_has_brokers(kafka_server, "broker", config.DEFAULT_BROKER_COUNT), "non-TLS enpoints expected"
    assert service_has_brokers(kafka_server, "broker-tls", config.DEFAULT_BROKER_COUNT), "TLS enpoints expected"

    updated_brokers = service_get_brokers(kafka_server, "broker-tls")
    assert set(brokers) == set(updated_brokers), "Brokers should not change"

    non_tls_brokers = service_get_brokers(kafka_server, "broker")

    assert set(_get_hostnames(brokers)) == set(_get_hostnames(non_tls_brokers)), "TLS and non-TLS broker " \
                                                                                 "hostnames should match"

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker", None)
    assert write_success, "Write failed"
    assert read_successes, "Read failed: MESSAGES={} read_successes={}".format(MESSAGES, read_successes)

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker-tls", None)
    assert write_success, "Write failed (TLS)"
    assert read_successes, "Read failed (TLS): MESSAGES={} read_successes={}".format(MESSAGES, read_successes)


@pytest.mark.incremental
def test_forward_kerberos_off_tls_off_plaintext_off(kafka_client, kafka_server):
    update_options = {
        "service": {
            "security": {
                "transport_encryption": {
                    "enabled": False,
                    "allow_plaintext": False
                }
            }
        }
    }

    brokers = service_get_brokers(kafka_server, "broker")

    update_service(kafka_server["package_name"], kafka_server["service"]["name"], update_options)

    assert service_has_brokers(kafka_server, "broker", config.DEFAULT_BROKER_COUNT), "non-TLS enpoints expected"
    assert not service_has_brokers(kafka_server, "broker-tls", config.DEFAULT_BROKER_COUNT), "TLS enpoints expected"

    updated_brokers = service_get_brokers(kafka_server, "broker")
    assert set(brokers) == set(updated_brokers), "Brokers should not change"

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker", None)
    assert write_success, "Write failed"
    assert read_successes, "Read failed: MESSAGES={} read_successes={}".format(MESSAGES, read_successes)


# We now run the tests in the oposite direction
@pytest.mark.incremental
def test_reverse_kerberos_off_tls_on_plaintext_on(kafka_client, kafka_server, kerberos):
    test_forward_kerberos_on_tls_on_plaintext_on(kafka_client, kafka_server, None)


@pytest.mark.incremental
def test_reverse_kerberos_off_tls_on_plaintext_off(kafka_client, kafka_server, kerberos):
    test_forward_kerberos_on_tls_on_plaintext_off(kafka_client, kafka_server, None)


@pytest.mark.incremental
def test_reverse_kerberos_on_tls_on_plaintext_off(kafka_client, kafka_server, kerberos):
    update_options = {
        "service": {
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc": {
                        "hostname": kerberos.get_host(),
                        "port": int(kerberos.get_port())
                    },
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            }
        }
    }

    brokers = service_get_brokers(kafka_server, "broker-tls")

    update_service(kafka_server["package_name"], kafka_server["service"]["name"], update_options)

    assert not service_has_brokers(kafka_server, "broker"), "non-TLS enpoints not expected"
    assert service_has_brokers(kafka_server, "broker-tls", config.DEFAULT_BROKER_COUNT), "TLS enpoints expected"

    updated_brokers = service_get_brokers(kafka_server, "broker-tls")
    assert set(brokers) == set(updated_brokers), "Brokers should not change"

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker-tls", kerberos)
    assert write_success, "Write failed"
    assert read_successes, "Read failed: MESSAGES={} read_successes={}".format(MESSAGES, read_successes)


@pytest.mark.incremental
def test_reverse_kerberos_on_tls_on_plaintext_on(kafka_client, kafka_server, kerberos):
    update_options = {
        "service": {
            "security": {
                "transport_encryption": {
                    "enabled": True,
                    "allow_plaintext": True
                }
            }
        }
    }

    brokers = service_get_brokers(kafka_server, "broker-tls")

    update_service(kafka_server["package_name"], kafka_server["service"]["name"], update_options)

    assert service_has_brokers(kafka_server, "broker", config.DEFAULT_BROKER_COUNT), "non-TLS enpoints expected"
    assert service_has_brokers(kafka_server, "broker-tls", config.DEFAULT_BROKER_COUNT), "TLS enpoints expected"

    updated_brokers = service_get_brokers(kafka_server, "broker-tls")
    assert set(brokers) == set(updated_brokers), "Brokers should not change"

    non_tls_brokers = service_get_brokers(kafka_server, "broker")

    assert set(_get_hostnames(brokers)) == set(_get_hostnames(non_tls_brokers)), "TLS and non-TLS broker " \
                                                                                 "hostnames should match"

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker", kerberos)
    assert write_success, "Write failed"
    assert read_successes, "Read failed: MESSAGES={} read_successes={}".format(MESSAGES, read_successes)

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker-tls", kerberos)
    assert write_success, "Write failed (TLS)"
    assert read_successes, "Read failed (TLS): MESSAGES={} read_successes={}".format(MESSAGES, read_successes)


@pytest.mark.incremental
def test_reverse_kerberos_on_tls_off_plaintext_off(kafka_client, kafka_server, kerberos):
    update_options = {
        "service": {
            "security": {
                "transport_encryption": {
                    "enabled": False,
                    "allow_plaintext": False
                }
            }
        }
    }

    brokers = service_get_brokers(kafka_server, "broker")

    update_service(kafka_server["package_name"], kafka_server["service"]["name"], update_options)

    assert service_has_brokers(kafka_server, "broker", config.DEFAULT_BROKER_COUNT), "non-TLS enpoints expected"
    assert not service_has_brokers(kafka_server, "broker-tls"), "TLS enpoints not expected"

    updated_brokers = service_get_brokers(kafka_server, "broker")
    assert set(brokers) == set(updated_brokers), "Brokers should not change"

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker", kerberos)
    assert write_success, "Write failed"
    assert read_successes, "Read failed: MESSAGES={} read_successes={}".format(MESSAGES, read_successes)


@pytest.mark.incremental
def test_reverse_kerberos_off_tls_off_plaintext_off(kafka_client, kafka_server):
    update_options = {
        "service": {
            "security": {
                "kerberos": {
                    "enabled": False,
                }
            }
        }
    }

    brokers = service_get_brokers(kafka_server, "broker")

    update_service(kafka_server["package_name"], kafka_server["service"]["name"], update_options)

    assert service_has_brokers(kafka_server, "broker", config.DEFAULT_BROKER_COUNT), "non-TLS enpoints expected"
    assert not service_has_brokers(kafka_server, "broker-tls"), "TLS enpoints not expected"

    updated_brokers = service_get_brokers(kafka_server, "broker")
    assert set(brokers) == set(updated_brokers), "Brokers should not change"

    write_success, read_successes = client_can_read_and_write("client", kafka_client, kafka_server,
                                                              "broker", None)
    assert write_success, "Write failed (TLS)"
    assert read_successes, "Read failed (TLS): MESSAGES={} read_successes={}".format(MESSAGES, read_successes)


def _get_hostnames(broker_list: list) -> list:
    return map(lambda b: b.split(":")[0], broker_list)


def update_service(package_name: str, service_name: str, options: dict):
    with tempfile.NamedTemporaryFile("w", suffix=".json") as f:
        options_path = f.name

        log.info("Writing updated options to %s", options_path)
        json.dump(options, f)
        f.flush()

        cmd = ["update", "start", "--options={}".format(options_path)]
        sdk_cmd.svc_cli(package_name, service_name, " ".join(cmd))

        # An update plan is a deploy plan
        sdk_plan.wait_for_kicked_off_deployment(service_name)
        sdk_plan.wait_for_completed_deployment(service_name)


def service_get_brokers(kafka_server: dict, endpoint_name: str) -> list:
    brokers = sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "endpoint {}".format(endpoint_name), json=True)["dns"]

    return brokers


def service_has_brokers(kafka_server: dict, endpoint_name: str, number_of_brokers: int=None) -> bool:
    endpoints = sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "endpoint", json=True)

    if endpoint_name not in endpoints:
        log.error("Expecting endpoint %s. Found %s", endpoint_name, endpoints)
        return False

    brokers = service_get_brokers(kafka_server, endpoint_name)
    return number_of_brokers == len(brokers)


def client_can_read_and_write(test_id: str,
                              kafka_client: dict, kafka_server: dict,
                              endpoint_name: str, krb5: object=None) -> tuple:
    client_id = kafka_client["id"]

    brokers_list = service_get_brokers(kafka_server, endpoint_name)
    broker_hosts = map(lambda b: b.split(":")[0], brokers_list)
    brokers = ",".join(brokers_list)

    if not sdk_cmd.resolve_hosts(kafka_client["id"], broker_hosts):
        log.error("Failed to resolve brokers: %s", broker_hosts)
        return False, []

    topic_name = kafka_client["env"]["KAFKA_TOPIC"]
    sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"],
                    "topic create {}".format(topic_name),
                    json=True)

    test_utils.wait_for_topic(kafka_server["package_name"], kafka_server["service"]["name"], topic_name)

    message = str(uuid.uuid4())

    security_options = {"is-tls": endpoint_name == "broker-tls",
                        "kerberos": krb5}

    write_success = write_to_topic(test_id, client_id, topic_name, message, brokers, security_options)
    if write_success:
        MESSAGES.append(message)

    read_messages = read_from_topic(test_id, client_id, topic_name, len(MESSAGES), brokers, security_options)

    read_success = map(lambda m: m in read_messages, MESSAGES)

    return write_success, read_success


def get_settings(cn: str, task: str, security_options: bool) -> tuple:

    is_tls = security_options.get("is-tls", False)

    kerberos_options = security_options.get("kerberos", None)
    is_kerberos = kerberos_options is not None

    properties = []
    environment = None

    if is_kerberos:
        properties.extend(auth.get_kerberos_client_properties(ssl_enabled=is_tls))
        environment = auth.setup_krb5_env(cn, task, kerberos_options)

    if is_tls:
        properties.extend(auth.get_ssl_client_properties(cn, has_kerberos=is_kerberos))

    return properties, environment


def write_to_topic(cn: str, task: str, topic: str, message: str, brokers: str, tls: bool) -> bool:

    properties, environment = get_settings(cn, task, tls)
    return auth.write_to_topic(cn, task, topic, message, properties, environment, brokers)


def read_from_topic(cn: str, task: str, topic: str, messages: int, brokers: str, tls: bool) -> str:

    properties, environment = get_settings(cn, task, tls)
    return auth.read_from_topic(cn, task, topic, messages, properties, environment, brokers)
