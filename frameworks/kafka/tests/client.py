"""
A collection of client utilites for Kafka.
"""
import logging
import uuid

import sdk_auth
import sdk_cmd
import sdk_marathon
import sdk_utils
import sdk_networks

from tests import auth
from tests import test_utils
from tests import topics


log = logging.getLogger(__name__)


class KafkaService:
    """
    A light wrapper around a Kafka service installed as part of the integration tests.
    """

    def __init__(self, service_options: dict):
        self._package_name = service_options["package_name"]
        self._service_name = service_options["service"]["name"]

    def get_zookeeper_connect(self) -> str:
        return sdk_networks.get_endpoint_string(
            self._package_name, self._service_name, "zookeeper"
        )

    def get_brokers_endpoints(self, endpoint_name: str) -> list:
        brokers = sdk_networks.get_endpoint(
            self._package_name, self._service_name, endpoint_name
        )["dns"]

        return brokers

    def wait_for_topic(self, topic_name: str):
        if not topic_name:
            return True

        test_utils.wait_for_topic(self._package_name, self._service_name, topic_name)

        return True


class KafkaClient:
    def __init__(self, id: str):

        self.id = id

        self._is_kerberos = False
        self._is_tls = False

        self.reset()

    def reset(self):
        self.MESSAGES = []
        self.brokers = None
        self.topic_name = None

    def get_id(self) -> str:
        return self.id

    @staticmethod
    def _get_kerberos_options(kerberos: sdk_auth.KerberosEnvironment) -> dict:
        options = {
            "container": {
                "volumes": [
                    {
                        "containerPath": "/tmp/kafkaconfig/kafka-client.keytab",
                        "secret": "kafka_keytab",
                    }
                ]
            },
            "secrets": {"kafka_keytab": {"source": kerberos.get_keytab_path()}},
        }

        return options

    def install(self, kerberos: sdk_auth.KerberosEnvironment = None) -> dict:
        options = {
            "id": self.id,
            "mem": 512,
            "container": {
                "type": "MESOS",
                "docker": {"image": "elezar/kafka-client:deca3d0", "forcePullImage": True},
            },
            "networks": [{"mode": "host"}],
            "env": {
                "JVM_MaxHeapSize": "512",
                "KAFKA_CLIENT_MODE": "test",
                "KAFKA_TOPIC": "securetest",
            },
        }

        if kerberos is not None:
            self._is_kerberos = True
            options = sdk_utils.merge_dictionaries(options, self._get_kerberos_options(kerberos))

        sdk_marathon.install_app(options)

        return options

    def uninstall(self):
        sdk_marathon.destroy_app(self.id)

    def _get_cli_settings(self, user: str, kerberos: sdk_auth.KerberosEnvironment):
        properties = []
        environment = None

        if self._is_kerberos:
            properties.extend(auth.get_kerberos_client_properties(ssl_enabled=self._is_tls))
            environment = auth.setup_krb5_env(user, self.id, kerberos)

        if self._is_tls:
            properties.extend(auth.get_ssl_client_properties(user, has_kerberos=self._is_kerberos))

        return properties, environment

    def get_endpoint_name(self) -> str:
        if self._is_tls:
            return "broker-tls"

        return "broker"

    def wait_for(self, kafka_server: dict, topic_name: str) -> bool:
        """
        Wait for the service to be visible from a client perspective.
        """
        service = KafkaService(kafka_server)

        if not self.brokers:
            brokers_list = service.get_brokers_endpoints(self.get_endpoint_name())
            broker_hosts = map(lambda b: b.split(":")[0], brokers_list)
            brokers = ",".join(brokers_list)

            if not sdk_cmd.resolve_hosts(self.id, broker_hosts, bootstrap_cmd="/opt/bootstrap"):
                log.error("Failed to resolve brokers: %s", broker_hosts)
                return False
            self.brokers = brokers

            return True

        if self.topic_name != topic_name:
            service.wait_for_topic(topic_name)
            self.topic_name = topic_name

        return True

    def connect(self, kafka_server: dict) -> bool:
        self.reset()
        return self.wait_for(kafka_server, topic_name=None)

    def can_write_and_read(
        self, user: str, kafka_server: dict, topic_name: str, krb5: sdk_auth.KerberosEnvironment
    ) -> tuple:

        if not self.wait_for(kafka_server, topic_name):
            return False, [], []

        write_success = self.write_to_topic(user, topic_name, self.brokers, krb5)
        read_sucesses, read_messages = self.read_from_topic(user, topic_name, self.brokers, krb5)

        return write_success, read_sucesses, read_messages

    def read_from_topic(
        self, user: str, topic_name: str, brokers: str, krb5: sdk_auth.KerberosEnvironment
    ) -> list:

        properties, environment = self._get_cli_settings(user, krb5)
        read_messages = auth.read_from_topic(
            user, self.id, topic_name, len(self.MESSAGES), properties, environment, brokers
        )

        read_success = map(lambda m: m in read_messages, self.MESSAGES)

        return read_success, read_messages

    def write_to_topic(
        self, user: str, topic_name: str, brokers: str, krb5: sdk_auth.KerberosEnvironment
    ) -> bool:

        # Generate a unique message:
        message = str(uuid.uuid4())

        properties, environment = self._get_cli_settings(user, krb5)
        write_success = auth.write_to_topic(
            user, self.id, topic_name, message, properties, environment, brokers
        )

        if write_success:
            self.MESSAGES.append(message)

        return write_success

    def add_acls(self, user: str, kafka_server: dict, topic_name: str):
        service = KafkaService(kafka_server)

        # TODO: If zookeeper has Kerberos enabled, then the environment should be changed
        environment = None
        topics.add_acls(user, self.id, topic_name, service.get_zookeeper_connect(), environment)

    def remove_acls(self, user: str, kafka_server: dict, topic_name: str):
        service = KafkaService(kafka_server)

        # TODO: If zookeeper has Kerberos enabled, then the environment should be changed
        environment = None
        topics.remove_acls(user, self.id, topic_name, service.get_zookeeper_connect(), environment)
