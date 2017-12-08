import logging

import sdk_tasks


LOG = logging.getLogger(__name__)


def add_acls(user: str, task: str, topic: str, zookeeper_endpoint: str, env_str=None):
    """
    Add Porducer and Consumer ACLs for the specifed user and topic
    """

    _add_role_acls("producer", user, task, topic, zookeeper_endpoint, env_str)
    _add_role_acls("consumer --group=*", user, task, topic, zookeeper_endpoint, env_str)

def _add_role_acls(role: str, user: str, task: str, topic: str, zookeeper_endpoint: str, env_str=None):
    cmd = "bash -c \"{setup_env}kafka-acls \
        --topic {topic_name} \
        --authorizer-properties zookeeper.connect={zookeeper_endpoint} \
        --add \
        --allow-principal User:{user} \
        --{role}\"".format(setup_env="{}  && ".format(env_str) if env_str else "",
                                                     topic_name=topic,
                                                     zookeeper_endpoint=zookeeper_endpoint,
                                                     user=user,
                                                     role=role)
    LOG.info("Running: %s", cmd)
    output = sdk_tasks.task_exec(task, cmd)
    LOG.info(output)
