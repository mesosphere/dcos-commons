import logging

import sdk_tasks


LOG = logging.getLogger(__name__)


def add_acls(user: str, task: str, topic: str, zookeeper_dns: list, env_str=None):
    """
    Add Read/Write ACLs for the specifed user and topic
    """

    zookeeper_connect = ",".join(zookeeper_dns)

    cmd = "bash -c \"{setup_env}kafka-acls \
        --topic {topic_name} \
        --authorizer-properties zookeeper.connect={zookeeper_connect} \
        --add \
        --allow-principal User:{user} \
        --operation Read --operation Write\"".format(setup_env="{}  && ".format(env_str) if env_str else "",
                                                     topic_name=topic,
                                                     zookeeper_connect=zookeeper_connect,
                                                     user=user)
    LOG.info("Running: %s", cmd)
    output = sdk_tasks.task_exec(task, cmd)
    LOG.info(output)
