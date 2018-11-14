"""
A collection of client utilites for Kafka.
"""
import logging
import uuid
import typing
import json
import retrying

from sdk.testing import sdk_auth
from sdk.testing import sdk_cmd
from sdk.testing import sdk_marathon
from sdk.testing import sdk_networks
from sdk.testing import sdk_utils

from tests import auth
from tests import topics


log = logging.getLogger(__name__)


class KafkaService:
    """
    A light wrapper around a Kafka service installed as part of the integration tests.
    """

    def __init__(self, package_name: str, service_name: str) -> None:
        self._package_name = package_name
        self._service_name = service_name

    def get_zookeeper_endpoint(self) -> str:
        return sdk_networks.get_endpoint_string(self._package_name, self._service_name, "zookeeper")

    def get_endpoint_dns(self, endpoint_name: str) -> list:
        return sdk_networks.get_endpoint(self._package_name, self._service_name, endpoint_name)[
            "dns"
        ]

    def wait_for_topic(self, topic_name: typing.Optional[str]):
        if not topic_name:
            return True

        @retrying.retry(
            stop_max_delay=5 * 60 * 1000,
            wait_exponential_multiplier=1000,
            wait_exponential_max=60 * 1000,
        )
        def wait(topic):
            self.get_topic_information(topic_name)

        return True

    def create_topic(self, topic_name: str) -> None:
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self._package_name, self._service_name, "topic create {}".format(topic_name)
        )
        assert rc == 0, "Topic create failed: {}".format(stderr)
        create_info = json.loads(stdout)
        assert 'Created topic "{}".\n'.format(topic_name) in create_info["message"]
        if "." in topic_name or "_" in topic_name:
            assert (
                "topics with a period ('.') or underscore ('_') could collide."
                in create_info["message"]
            )

    def delete_topic(self, topic_name: str) -> None:
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self._package_name, self._service_name, "topic delete {}".format(topic_name)
        )
        assert rc == 0, "Topic delete failed: {}".format(stderr)
        delete_info = json.loads(stdout)
        assert len(delete_info) == 1
        assert delete_info["message"].startswith(
            "Output: Topic {} is marked for deletion".format(topic_name)
        )

    def get_topics(self) -> dict:
        rc, stdout, stderr = sdk_cmd.svc_cli(self._package_name, self._service_name, "topic list")
        assert rc == 0, "Topic list query failed: {}".format(stderr)
        return json.loads(stdout)

    def get_brokers(self) -> typing.Tuple[int, str, str]:
        return sdk_cmd.svc_cli(self._package_name, self._service_name, "broker list")

    def get_topic_information(self, topic_name: str) -> dict:
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self._package_name, self._service_name, "topic describe {}".format(topic_name)
        )
        assert rc == 0, "Topic describe failed: {}".format(stderr)
        return json.loads(stdout)

    def get_topic_partition_information(self, topic_name: str, partition_count: int) -> dict:
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self._package_name,
            self._service_name,
            "topic partitions {} {}".format(topic_name, partition_count),
        )
        assert rc == 0, "Partition info failed: {}".format(stderr)
        return json.loads(stdout)


class KafkaClient:
    def __init__(
        self,
        id: str,
        package_name: str,
        service_name: str,
        kerberos: typing.Optional[sdk_auth.KerberosEnvironment] = None,
    ) -> None:

        self.kafka_service = KafkaService(package_name, service_name)
        self.id = id
        self.kerberos = kerberos

        self._is_tls = False

        self.reset()

    def reset(self):
        self.MESSAGES = []
        self.brokers = None

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

    def install(self) -> dict:
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

        if self.kerberos:
            options = sdk_utils.merge_dictionaries(
                options, self._get_kerberos_options(self.kerberos)
            )

        sdk_marathon.install_app(options)

        return options

    def uninstall(self):
        sdk_marathon.destroy_app(self.id)

    def _get_cli_settings(self, user: str):
        properties = []
        environment = None

        if self.kerberos:
            properties.extend(auth.get_kerberos_client_properties(ssl_enabled=self._is_tls))
            environment = auth.setup_krb5_env(user, self.id, self.kerberos)

        if self._is_tls:
            properties.extend(
                auth.get_ssl_client_properties(user, has_kerberos=self.kerberos is not None)
            )

        return properties, environment

    def get_endpoint_name(self) -> str:
        if self._is_tls:
            return "broker-tls"

        return "broker"

    def wait_for(self, topic_name: typing.Optional[str] = None) -> bool:
        """
        Wait for the service to be visible from a client perspective.
        """
        if not self.brokers:
            brokers_list = self.kafka_service.get_endpoint_dns(self.get_endpoint_name())
            broker_hosts = map(lambda b: b.split(":")[0], brokers_list)
            brokers = ",".join(brokers_list)

            if not sdk_cmd.resolve_hosts(self.id, broker_hosts, bootstrap_cmd="/opt/bootstrap"):
                log.error("Failed to resolve brokers: %s", broker_hosts)
                return False
            self.brokers = brokers

            return True

        if topic_name:
            self.kafka_service.wait_for_topic(topic_name)

        return True

    def connect(self, broker_count: int) -> bool:
        self.reset()
        self.check_broker_count(broker_count)
        return self.wait_for()

    def can_write_and_read(self, user: str, topic_name: str) -> tuple:

        if not self.wait_for(topic_name):
            return False, [], []

        write_success = self.write_to_topic(user, topic_name, self.brokers)
        read_sucesses, read_messages = self.read_from_topic(user, topic_name, self.brokers)

        return write_success, read_sucesses, read_messages

    def read_from_topic(
        self, user: str, topic_name: str, brokers: str
    ) -> typing.Tuple[typing.Iterator[bool], str]:

        properties, environment = self._get_cli_settings(user)
        read_messages = auth.read_from_topic(
            user, self.id, topic_name, len(self.MESSAGES), properties, environment, brokers
        )

        read_success = map(lambda m: m in read_messages, self.MESSAGES)

        return read_success, read_messages

    def write_to_topic(self, user: str, topic_name: str, brokers: str) -> bool:

        # Generate a unique message:
        message = str(uuid.uuid4())

        properties, environment = self._get_cli_settings(user)
        write_success = auth.write_to_topic(
            user, self.id, topic_name, message, properties, environment, brokers
        )

        if write_success:
            self.MESSAGES.append(message)

        return write_success

    def add_acls(self, user: str, topic_name: str):

        # TODO: If zookeeper has Kerberos enabled, then the environment should be changed
        environment = None
        topics.add_acls(
            user, self.id, topic_name, self.kafka_service.get_zookeeper_endpoint(), environment
        )

    def remove_acls(self, user: str, topic_name: str):
        # TODO: If zookeeper has Kerberos enabled, then the environment should be changed
        environment = None
        topics.remove_acls(
            user, self.id, topic_name, self.kafka_service.get_zookeeper_endpoint(), environment
        )

    def create_topic(self, topic_name: str) -> None:
        self.kafka_service.create_topic(topic_name)

    def check_topic_creation(self, topic_name: str) -> None:
        self.create_topic(topic_name)
        assert topic_name in self.kafka_service.get_topics()

    def check_topic_deletion(self, topic_name: str) -> None:
        self.kafka_service.delete_topic(topic_name)

    def check_topic_partition_count(self, topic_name: str, partition_count: int) -> None:
        information = self.kafka_service.get_topic_information(topic_name)
        assert "partitions" in information
        assert len(information["partitions"]) == partition_count

    def check_users_can_read_and_write(self, users: typing.List[str], topic_name: str) -> None:
        for user in users:
            log.info("Checking ability of write / read for user=%s", user)
            write_success, read_successes, _ = self.can_write_and_read(user, topic_name)
            assert write_success, "Write failed (user={})".format(user)
            assert read_successes, (
                "Read failed (user={}): "
                "MESSAGES={} "
                "read_successes={}".format(user, self.MESSAGES, read_successes)
            )

    def check_users_are_not_authorized_to_read_and_write(
        self, users: typing.List[str], topic_name: str
    ) -> None:
        for user in users:
            log.info("Checking lack of write / read permissions for user=%s", user)
            write_success, _, read_messages = self.can_write_and_read(user, topic_name)
            assert not write_success, "Write not expected to succeed (user={})".format(user)
            assert auth.is_not_authorized(read_messages), "Unauthorized expected (user={}".format(
                user
            )

    @retrying.retry(wait_fixed=1000, stop_max_delay=120 * 1000)
    def check_broker_count(self, count: int) -> None:
        rc, stdout, _ = self.kafka_service.get_brokers()
        assert rc == 0 and len(json.loads(stdout)) == count

    def check_topic_partition_change(self, topic_name: str, partition_count: int) -> str:
        partition_info = self.kafka_service.get_topic_partition_information(
            topic_name, partition_count
        )
        assert len(partition_info) == 1
        log.info("Partition info for %s: %s", topic_name, partition_info)
        assert partition_info["message"].startswith("Output: WARNING: If partitions are increased")
        return partition_info["message"]
