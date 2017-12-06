import logging

import sdk_tasks


LOG = logging.getLogger(__name__)


def add_acls(user: str, task: str, topic: str, zookeeper_endpoint: str, env_str=None):
    """
    Add Read/Write ACLs for the specifed user and topic
    """

    cmd = "bash -c \"{setup_env}kafka-acls \
        --topic {topic_name} \
        --authorizer-properties zookeeper.connect={zookeeper_endpoint} \
        --add \
        --allow-principal User:{user} \
        --consumer --group=* \
        --operation Read --operation Write\"".format(setup_env="{}  && ".format(env_str) if env_str else "",
                                                     topic_name=topic,
                                                     zookeeper_endpoint=zookeeper_endpoint,
                                                     user=user)
    LOG.info("Running: %s", cmd)
    output = sdk_tasks.task_exec(task, cmd)
    LOG.info(output)
