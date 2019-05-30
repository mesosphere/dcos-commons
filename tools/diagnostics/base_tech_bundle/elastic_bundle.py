import logging

import sdk_cmd

from base_tech_bundle import BaseTechBundle
import config

logger = logging.getLogger(__name__)


class ElasticBundle(BaseTechBundle):

    def __init__(self, package_name, service_name, scheduler_tasks, service, output_directory):
        super().__init__(package_name,
                         service_name,
                         scheduler_tasks,
                         service,
                         output_directory)

    @config.retry
    def task_exec(self, task_id, cmd):
        full_cmd = " ".join(
            [
                "export JAVA_HOME=$(ls -d ${MESOS_SANDBOX}/jdk*/) &&",
                "export TASK_IP=$(${MESOS_SANDBOX}/bootstrap --get-task-ip) &&",
                "ELASTICSEARCH_DIRECTORY=$(ls -d ${MESOS_SANDBOX}/elasticsearch-*/) &&",
                cmd,
            ]
        )

        return sdk_cmd.marathon_task_exec(task_id, "bash -c '{}'".format(full_cmd))

    def create_stats_file(self, task_id):
        command = "curl -s ${MESOS_CONTAINER_IP}:${PORT_HTTP}/_stats"
        rc, stdout, stderr = self.task_exec(task_id, command)

        if rc != 0:
            logger.error(
                "Could not get Elasticsearch /_stats. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'", rc, stdout, stderr
            )
        else:
            if stderr:
                logger.warning("Non-fatal Elasticsearch /_stats message\nstderr: '%s'", stderr)
            self.write_file("elasticsearch_stats_{}.json".format(task_id), stdout)

    def create_tasks_stats_files(self):
        self.for_each_running_task_with_prefix("master", self.create_stats_file)

    def create(self):
        logger.info("Creating Elastic bundle")
        self.create_configuration_file()
        self.create_pod_status_file()
        self.create_plans_status_files()
        self.create_tasks_stats_files()
