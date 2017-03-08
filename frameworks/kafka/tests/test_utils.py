import pytest

import json
import sdk_cmd as command

from tests.config import (
    DEFAULT_PARTITION_COUNT,
    DEFAULT_REPLICATION_FACTOR,
    PACKAGE_NAME,
    DEFAULT_BROKER_COUNT
)

SERVICE_NAME = PACKAGE_NAME

DEFAULT_TOPIC_NAME = 'topic1'
EPHEMERAL_TOPIC_NAME = 'topic_2'

STATIC_PORT_OPTIONS_DICT = {"brokers": {"port": 9092 }}
DYNAMIC_PORT_OPTIONS_DICT = {"brokers": {"port": 0}}


POD_TYPE = "kafka"


def service_cli(cmd_str):
    full_cmd = '{} {}'.format(PACKAGE_NAME, cmd_str)
    ret_str = command.run_cli(full_cmd)
    return json.loads(ret_str)
