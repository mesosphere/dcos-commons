import os

import json
import logging

import shakedown
import sdk_cmd
import sdk_tasks

from tests.config import (
    PACKAGE_NAME,
    SERVICE_NAME,
    DEFAULT_PARTITION_COUNT,
    DEFAULT_BROKER_COUNT,
    DEFAULT_POD_TYPE,
    DEFAULT_TASK_NAME
)

log = logging.getLogger(__name__)

DEFAULT_TOPIC_NAME = 'topic1'
EPHEMERAL_TOPIC_NAME = 'topic_2'


def service_cli(cmd_str, get_json=True, print_output=True, service_name=SERVICE_NAME):
    full_cmd = '{} --name={} {}'.format(PACKAGE_NAME, service_name, cmd_str)
    ret_str = sdk_cmd.run_cli(full_cmd, print_output=print_output)
    if get_json:
        return json.loads(ret_str)
    else:
        return ret_str


def broker_count_check(count, service_name=SERVICE_NAME):
    def fun():
        try:
            if len(service_cli('broker list', service_name=service_name)) == count:
                return True
        except:
            pass
        return False

    shakedown.wait_for(fun)


def restart_broker_pods(service_name=SERVICE_NAME):
    for i in range(DEFAULT_BROKER_COUNT):
        broker_id = sdk_tasks.get_task_ids(service_name,'{}-{}-{}'.format(DEFAULT_POD_TYPE, i, DEFAULT_TASK_NAME))
        restart_info = service_cli('pod restart {}-{}'.format(DEFAULT_POD_TYPE, i), service_name=service_name)
        sdk_tasks.check_tasks_updated(service_name, '{}-{}-{}'.format(DEFAULT_POD_TYPE, i, DEFAULT_TASK_NAME), broker_id)
        sdk_tasks.check_running(service_name, DEFAULT_BROKER_COUNT)
        assert len(restart_info) == 2
        assert restart_info['tasks'][0] == '{}-{}-{}'.format(DEFAULT_POD_TYPE, i, DEFAULT_TASK_NAME)


def replace_broker_pod(service_name=SERVICE_NAME):
    broker_0_id = sdk_tasks.get_task_ids(service_name, '{}-0-{}'.format(DEFAULT_POD_TYPE, DEFAULT_TASK_NAME))
    service_cli('pod replace {}-0'.format(DEFAULT_POD_TYPE), service_name=service_name)
    sdk_tasks.check_tasks_updated(service_name, '{}-0-{}'.format(DEFAULT_POD_TYPE, DEFAULT_TASK_NAME), broker_0_id)
    sdk_tasks.check_running(service_name, DEFAULT_BROKER_COUNT)
    # wait till all brokers register
    broker_count_check(DEFAULT_BROKER_COUNT, service_name=service_name)


def create_topic(service_name=SERVICE_NAME):
    create_info = service_cli('topic create {}'.format(EPHEMERAL_TOPIC_NAME), service_name=service_name)
    log.info(create_info)
    assert ('Created topic "%s".\n' % EPHEMERAL_TOPIC_NAME in create_info['message'])
    assert ("topics with a period ('.') or underscore ('_') could collide." in create_info['message'])
    topic_list_info = service_cli('topic list', service_name=service_name)
    assert topic_list_info == [EPHEMERAL_TOPIC_NAME]

    topic_info = service_cli('topic describe {}'.format(EPHEMERAL_TOPIC_NAME), service_name=service_name)
    assert len(topic_info) == 1
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT


def delete_topic(service_name=SERVICE_NAME):
    delete_info = service_cli('topic delete {}'.format(EPHEMERAL_TOPIC_NAME), service_name=service_name)

    assert len(delete_info) == 1
    assert delete_info['message'].startswith('Output: Topic {} is marked for deletion'.format(EPHEMERAL_TOPIC_NAME))

    topic_info = service_cli('topic describe {}'.format(EPHEMERAL_TOPIC_NAME), service_name=service_name)
    assert len(topic_info) == 1
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT


def create_service_account(name, secret_name=None):
    """
    Creates a service account, a secret containing private key and uid and
    assigns `superuser` permissions to the account.

    Args:
        name (str): Name of the user account
        secret_name (str): Optionally name of secret. If not provided service
            account name will be used.
    """
    if secret_name is None:
        secret_name = name

    sdk_cmd.run_cli(
        "security org service-accounts keypair private-key.pem public-key.pem")
    sdk_cmd.run_cli(
        'security org service-accounts create -p public-key.pem '
        '-d "My service account" {name}'.format(
            name=name)
        )
    sdk_cmd.run_cli(
        "security secrets create-sa-secret private-key.pem "
        "{name} {secret_name}".format(
            name=name,
            secret_name=secret_name)
        )
    # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
    sdk_cmd.run_cli(
        "security org groups add_user superusers {name}".format(name=name))


def delete_service_account(name, secret_name=None):
    """
    Deletes service account and secret with private key that belongs to the
    service account.

    Args:
        name (str): Name of the user account
        secret_name (str): Optionally name of secret. If not provided service
            account name will be used.
    """
    if secret_name is None:
        secret_name = name

    sdk_cmd.run_cli(
        "security org service-accounts delete {name}".format(name=name))
    sdk_cmd.run_cli(
        "security secrets delete {secret_name}".format(secret_name=secret_name))

    # Files generated by service-accounts keypair command should get removed
    keypair_files = ['private-key.pem', 'public-key.pem']
    for keypair_file in keypair_files:
        os.unlink(keypair_file)
