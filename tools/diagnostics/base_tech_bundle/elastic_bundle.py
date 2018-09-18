import logging

import sdk_cmd

from base_tech_bundle import BaseTechBundle
import config

logger = logging.getLogger(__name__)


class ElasticBundle(BaseTechBundle):
    @config.retry
    def task_exec(self, task_id, cmd):
        full_cmd = " ".join(
            [
                "export JAVA_HOME=$(ls -d ${MESOS_SANDBOX}/jdk*/jre/) &&",
                "export TASK_IP=$(${MESOS_SANDBOX}/bootstrap --get-task-ip) &&",
                "ELASTICSEARCH_DIRECTORY=$(ls -d ${MESOS_SANDBOX}/elasticsearch-*/) &&",
                cmd,
            ]
        )

        return sdk_cmd.marathon_task_exec(task_id, "bash -c '{}'".format(full_cmd))

    def create_stats_file(self, task_id):
        command = "curl -s ${MESOS_CONTAINER_IP}:${PORT_HTTP}/_stats"
        rc, stdout, stderr = self.task_exec(task_id, command)
        if rc != 0 or stderr:
            logger.error(
                "Could not get Elasticsearch /_stats\nstdout: '%s'\nstderr: '%s'", stdout, stderr
            )
        else:
            self.write_file("elasticsearch_stats_{}.json".format(task_id), stdout)

    def create_tasks_stats_files(self):
        self.for_each_running_task_with_prefix("master", self.create_stats_file)

    def create(self):
        logger.info("Creating Elastic bundle")
        self.create_tasks_stats_files()
