import json
import pytest

import sdk_cmd
import sdk_fault_domain
import sdk_install
import sdk_utils

from tests import config, client

FOLDERED_NAME = sdk_utils.get_foldered_name(config.SERVICE_NAME)


@pytest.fixture(scope="module")
def kafka_client():
    try:
        kafka_client = client.KafkaClient("kafka-client", config.PACKAGE_NAME, FOLDERED_NAME)
        kafka_client.install()
        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.11")
@sdk_utils.dcos_ee_only
def test_zones_not_referenced_in_placement_constraints(kafka_client: client.KafkaClient):

    sdk_install.uninstall(config.PACKAGE_NAME, FOLDERED_NAME)
    sdk_install.install(
        config.PACKAGE_NAME,
        FOLDERED_NAME,
        config.DEFAULT_BROKER_COUNT,
        additional_options={"service": {"name": FOLDERED_NAME}},
    )

    kafka_client.connect(config.DEFAULT_BROKER_COUNT)

    rc, stdout, _ = sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_NAME, "broker list")
    assert rc == 0, "Broker list command failed"

    for broker_id in json.loads(stdout):
        rc, stdout, _ = sdk_cmd.svc_cli(
            config.PACKAGE_NAME, FOLDERED_NAME, "broker get {}".format(broker_id)
        )
        assert rc == 0, "Broker get command failed"

        assert json.loads(stdout).get("rack") is None

    sdk_install.uninstall(config.PACKAGE_NAME, FOLDERED_NAME)


@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.11")
@sdk_utils.dcos_ee_only
def test_zones_referenced_in_placement_constraints(kafka_client: client.KafkaClient):

    sdk_install.uninstall(config.PACKAGE_NAME, FOLDERED_NAME)
    sdk_install.install(
        config.PACKAGE_NAME,
        FOLDERED_NAME,
        config.DEFAULT_BROKER_COUNT,
        additional_options={
            "service": {"name": FOLDERED_NAME, 
                        "brokers" {"placement_constraint": '[["@zone", "GROUP_BY"]]'}}
        },
    )

    kafka_client.connect(config.DEFAULT_BROKER_COUNT)

    rc, stdout, _ = sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_NAME, "broker list")
    assert rc == 0, "Broker list command failed"

    for broker_id in json.loads(stdout):
        rc, stdout, _ = sdk_cmd.svc_cli(
            config.PACKAGE_NAME, FOLDERED_NAME, "broker get {}".format(broker_id)
        )
        assert rc == 0, "Broker get command failed"

        assert sdk_fault_domain.is_valid_zone(json.loads(stdout).get("rack"))

    sdk_install.uninstall(config.PACKAGE_NAME, FOLDERED_NAME)
