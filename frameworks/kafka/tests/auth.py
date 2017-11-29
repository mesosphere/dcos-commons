import logging
import uuid
import pytest

import shakedown

import sdk_auth
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_repository
import sdk_tasks
import sdk_utils

from tests import config

LOG = logging.getLogger(__name__)


def wait_for_brokers(client: str, brokers: list):
    LOG.info("Running bootstrap to wait for DNS resolution")
    bootstrap_cmd = ['/opt/bootstrap',
                     '-resolve-hosts', ','.join(brokers), '-verbose']
    bootstrap_output = sdk_tasks.task_exec(client, ' '.join(bootstrap_cmd))
    LOG.info(bootstrap_output)


def send_and_receive_message(client: str):
    LOG.info("Starting send-recieve test")
    message = uuid.uuid4()
    producer_cmd = ['/tmp/kafkaconfig/start.sh', 'producer', str(message)]

    for i in range(2):
        LOG.info("Running(%s) %s", i, producer_cmd)
        producer_output = sdk_tasks.task_exec(client, ' '.join(producer_cmd))
        LOG.info("Producer output(%s): %s", i, producer_output)

    assert "Sent message: '{message}'".format(message=str(
        message)) in ' '.join(str(p) for p in producer_output)

    consumer_cmd = ['/tmp/kafkaconfig/start.sh', 'consumer', 'single']
    LOG.info("Running %s", consumer_cmd)
    consumer_output = sdk_tasks.task_exec(client, ' '.join(consumer_cmd))
    LOG.info("Consumer output: %s", consumer_output)

    assert str(message) in ' '.join(str(c) for c in consumer_output)
