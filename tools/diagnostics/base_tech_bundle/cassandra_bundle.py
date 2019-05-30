import logging

import sdk_cmd

from base_tech_bundle import BaseTechBundle
import config

logger = logging.getLogger(__name__)


class CassandraBundle(BaseTechBundle):

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
                "CASSANDRA_DIRECTORY=$(ls -d ${MESOS_SANDBOX}/apache-cassandra-*/) &&",
                cmd,
            ]
        )

        return sdk_cmd.marathon_task_exec(task_id, "bash -c '{}'".format(full_cmd))

    def create_nodetool_status_file(self, task_id):
        rc, stdout, stderr = self.task_exec(task_id, "${CASSANDRA_DIRECTORY}/bin/nodetool status")

        if rc != 0:
            logger.error(
                "Could not get Cassandra nodetool stats. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'", rc, stdout, stderr
            )
        else:
            if stderr:
                logger.warning("Non-fatal nodetool stats message\nstderr: '%s'", stderr)
            self.write_file("cassandra_nodetool_status_{}.txt".format(task_id), stdout)

    def create_nodetool_tpstats_file(self, task_id):
        rc, stdout, stderr = self.task_exec(task_id, "${CASSANDRA_DIRECTORY}/bin/nodetool tpstats")

        if rc != 0:
            logger.error(
                "Could not get Cassandra nodetool tpstats. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'", rc, stdout, stderr
            )
        else:
            if stderr:
                logger.warning("Non-fatal nodetool tpstats message\nstderr: '%s'", stderr)
            self.write_file("cassandra_nodetool_tpstats_{}.txt".format(task_id), stdout)

    def create_tasks_nodetool_status_files(self):
        self.for_each_running_task_with_prefix("node", self.create_nodetool_status_file)

    def create_tasks_nodetool_tpstats_files(self):
        self.for_each_running_task_with_prefix("node", self.create_nodetool_tpstats_file)

    def create(self):
        logger.info("Creating Cassandra bundle")
        self.create_configuration_file()
        self.create_pod_status_file()
        self.create_plans_status_files()
        self.create_tasks_nodetool_status_files()
        self.create_tasks_nodetool_tpstats_files()
