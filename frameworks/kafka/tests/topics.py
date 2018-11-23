import logging

import sdk_cmd

from tests import auth

LOG = logging.getLogger(__name__)


def add_acls(user: str, marathon_task: str, topic: str, zookeeper_endpoint: str, env_str=None):
    """
    Add Producer and Consumer ACLs for the specifed user and topic
    """

    _add_role_acls(["--producer"], user, marathon_task, topic, zookeeper_endpoint, env_str)
    _add_role_acls(
        ["--consumer", "--group=*"], user, marathon_task, topic, zookeeper_endpoint, env_str
    )


def remove_acls(user: str, marathon_task: str, topic: str, zookeeper_endpoint: str, env_str=None):
    """
    Remove Producer and Consumer ACLs for the specifed user and topic
    """
    _remove_role_acls(["--producer"], user, marathon_task, topic, zookeeper_endpoint, env_str)
    _remove_role_acls(
        ["--consumer", "--group=*"], user, marathon_task, topic, zookeeper_endpoint, env_str
    )


def _modify_role_acls(
    action: str,
    roles: list,
    user: str,
    marathon_task: str,
    topic: str,
    zookeeper_endpoint: str,
    env_str: str = None,
) -> tuple:

    if not action.startswith("--"):
        action = "--{}".format(action)

    cmd_list = [
        "kafka-acls",
        "--topic",
        topic,
        "--authorizer-properties",
        "zookeeper.connect={}".format(zookeeper_endpoint),
        action,
        "--force",
        "--allow-principal",
        "User:{}".format(user),
    ]
    cmd_list.extend(roles)

    cmd = auth.get_bash_command(" ".join(cmd_list), env_str)

    LOG.info("Running: %s", cmd)
    output = sdk_cmd.marathon_task_exec(marathon_task, cmd)
    LOG.info(output)

    return output


def _add_role_acls(
    roles: list,
    user: str,
    marathon_task: str,
    topic: str,
    zookeeper_endpoint: str,
    env_str: str = None,
) -> tuple:
    return _modify_role_acls("add", roles, user, marathon_task, topic, zookeeper_endpoint, env_str)


def _remove_role_acls(
    roles: list,
    user: str,
    marathon_task: str,
    topic: str,
    zookeeper_endpoint: str,
    env_str: str = None,
) -> tuple:
    return _modify_role_acls(
        "remove", roles, user, marathon_task, topic, zookeeper_endpoint, env_str
    )


def filter_empty_offsets(offsets: list, additional: list = []) -> list:
    ignored_offsets = [None, {}, {"0": ""}]
    ignored_offsets.extend(additional)
    LOG.info("Filtering %s from %s", ignored_offsets, offsets)

    remaining = [o for o in offsets if o not in ignored_offsets]

    LOG.info("Remaining offsets: %s", remaining)

    return remaining
