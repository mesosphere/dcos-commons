import logging

import sdk_cmd

from tests import auth

LOG = logging.getLogger(__name__)


def add_acls(user: str, task: str, topic: str, zookeeper_endpoint: str, env_str=None):
    """
    Add Porducer and Consumer ACLs for the specifed user and topic
    """

    _add_role_acls(["--producer", ], user, task, topic, zookeeper_endpoint, env_str)
    _add_role_acls(["--consumer", "--group=*"], user, task, topic, zookeeper_endpoint, env_str)


def _add_role_acls(roles: list, user: str, task: str, topic: str, zookeeper_endpoint: str, env_str: str=None):

    cmd_list = ["kafka-acls",
                "--topic", topic,
                "--authorizer-properties", "zookeeper.connect={}".format(zookeeper_endpoint),
                "--add",
                "--allow-principal", "User:{}".format(user), ]
    cmd_list.extend(roles)

    cmd = auth.get_bash_command(" ".join(cmd_list), env_str)

    LOG.info("Running: %s", cmd)
    output = sdk_cmd.task_exec(task, cmd)
    LOG.info(output)


def filter_empty_offsets(offsets: list, additional: list=[]) -> list:
    ignored_offsets = [None, {}, {"0": ""}]
    ignored_offsets.extend(additional)
    LOG.info("Filtering %s from %s", ignored_offsets, offsets)

    remaining = [o for o in offsets if o not in ignored_offsets]

    LOG.info("Remaining offsets: %s", remaining)

    return remaining
