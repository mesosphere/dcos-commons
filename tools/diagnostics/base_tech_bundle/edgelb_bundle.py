import json
import logging
import os

import agent
import config
import constants
import full_bundle
import sdk_cmd
import sdk_diag
from base_tech_bundle import BaseTechBundle

log = logging.getLogger(__name__)


class EdgeLBBundle(BaseTechBundle):
    """ diagnostic bundle of Edge-LB

    As the Edge-LB is not service, Edge-LB APIServer diagnostic bundle is
    collected through from Edge-LB pool
    """

    def __init__(self, package_name, service_name, scheduler_tasks, service,
                 output_directory):
        # Override package name as 'edgelb' rather than 'edgelb-pool'
        package_name = 'edgelb'

        # Override service name as 'edgelb' rather than
        # 'dcos-edgelb/pools/<pool-name>'
        service_name = 'edgelb'

        super().__init__(package_name, service_name, scheduler_tasks, service,
                         output_directory)

    @config.retry
    def create_version_file(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name,
            self.service_name,
            "version",
            print_output=False)

        if rc != 0:
            log.error(
                "Could not get service version. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'", rc, stdout, stderr)
        else:
            if stderr:
                log.warning("Non-fatal service version message\nstderr: '%s'",
                            stderr)
            self.write_file("edgelb_version", stdout)

    @config.retry
    def create_ping_file(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "ping", print_output=False)

        if rc != 0:
            log.error(
                "Could not ping service. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'", rc, stdout, stderr)
        else:
            if stderr:
                log.warning("Non-fatal service ping message\nstderr: '%s'",
                            stderr)
            self.write_file("edgelb_ping", stdout)

    @config.retry
    def create_pool_status_file(self, pool):
        pool_name = pool['name']
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name,
            self.service_name,
            "status {} --json".format(pool_name),
            print_output=False)

        if rc != 0:
            log.error(
                "Could not generate status for pool {}. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'".format(pool_name), rc, stdout,
                stderr)
        else:
            if stderr:
                log.warning(
                    "Non-fatal status {} message\nstderr: '%s'".format(
                        pool_name), stderr)
            self.write_file("edgelb_status_{}.json".format(pool_name), stdout)

    @config.retry
    def create_pool_endpoints_file(self, pool):
        pool_name = pool['name']
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name,
            self.service_name,
            "endpoints {} --json".format(pool_name),
            print_output=False)

        if rc != 0:
            log.error(
                "Could not generate endpoints for pool {}. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'".format(pool_name), rc, stdout,
                stderr)
        else:
            if stderr:
                log.warning(
                    "Non-fatal endpoints {} message\nstderr: '%s'".format(
                        pool_name), stderr)
            self.write_file("edgelb_endpoints_{}.json".format(pool_name),
                            stdout)

    @config.retry
    def create_pool_lb_config_file(self, pool):
        pool_name = pool['name']
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name,
            self.service_name,
            "lb-config {}".format(pool_name),
            print_output=False)

        if rc != 0:
            log.error(
                "Could not generate lb-config for pool {}. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'".format(pool_name), rc, stdout,
                stderr)
        else:
            if stderr:
                log.warning(
                    "Non-fatal lb-config {} message\nstderr: '%s'".format(
                        pool_name), stderr)
            self.write_file("edgelb_lb-config_{}".format(pool_name), stdout)

    @config.retry
    def create_pool_lb_template_file(self, pool):
        pool_name = pool['name']
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name,
            self.service_name,
            "template show {}".format(pool_name),
            print_output=False)

        if rc != 0:
            log.error(
                "Could not generate template show for pool {}. return-code: \
                '%s'\n stdout: '%s'\nstderr: '%s'".format(pool_name), rc,
                stdout, stderr)
        else:
            if stderr:
                log.warning(
                    "Non-fatal template show {} message\nstderr: \
                    '%s'".format(pool_name), stderr)
            self.write_file("edgelb_template_show_{}".format(pool_name),
                            stdout)

    @config.retry
    def create_pool_files(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name,
            self.service_name,
            "list --json",
            print_output=False)

        if rc != 0:
            log.error(
                "Could not generate pool list. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'", rc, stdout, stderr)
        else:
            if stderr:
                log.warning("Non-fatal pool list message\nstderr: '%s'",
                            stderr)

            try:
                pools = json.loads(stdout)
                # Write out full pools list.
                self.write_file("edgelb_pools_list.json", stdout)
                # Issue pool specific command.
                for pool in pools:
                    self.create_pool_status_file(pool)
                    self.create_pool_endpoints_file(pool)
                    self.create_pool_lb_config_file(pool)
                    self.create_pool_lb_template_file(pool)
            except Exception:
                log.error(
                    "Could not parse pool list json.\nstdout: '%s'\nstderr: '%s'",
                    stdout, stderr)

    def download_log_files(self):
        """ download log files of Edge-LB APIServer
        """
        success, all_services_or_error = full_bundle.get_dcos_services()
        if not success:
            log.error(all_services_or_error)
            return 1, self

        all_services = json.loads(all_services_or_error)
        marathon_services = [
            s for s in all_services
            if full_bundle.service_names_match("marathon", s.get("name"))
        ]
        if len(marathon_services) > 1:
            log.warn("More than one marathon services: %s",
                     len(marathon_services))

        active_marathon_services = [
            s for s in marathon_services if full_bundle.is_service_active(s)
        ]
        # TODO: handle the possibility of having more than one Marathon service?
        active_marathon_service = active_marathon_services[0]

        edgelb_task = {}
        for task in active_marathon_service["tasks"]:
            labels = task.get("labels", {})
            for label in labels:
                # service name label is not configurable, and we can definitely
                # locate the edgelb task with this.
                edgelb_service_label = {
                    "key": constants.SERVICE_NAME_LABEL_KEY,
                    "value": "edgelb"
                }
                if label == edgelb_service_label:
                    edgelb_task = task
                    break
            if edgelb_task:
                break

        if not edgelb_task:
            log.error("Could not find Edge-LB APIServer task")
            return

        task_agent_id = edgelb_task.get("slave_id")
        task_id = edgelb_task.get("id")
        agent_executor_paths = agent.debug_agent_files(task_agent_id)
        task_executor_sandbox_path = sdk_diag._find_matching_executor_path(
            agent_executor_paths, sdk_diag._TaskEntry(edgelb_task))
        if not task_executor_sandbox_path:
            log.error(
                "Could not find executor sandbox path for task '%s'. \
                      This probably means that its agent ('%s') is missing",
                edgelb_task["id"], edgelb_task["slave_id"])
            return

        agent.download_task_files(
            task_agent_id,
            task_executor_sandbox_path,
            task_id,
            os.path.join(self.output_directory, "tasks"),
            constants.TASK_LOG_FILES_PATTERNS,
        )

    def create(self):
        log.info("Creating EdgeLB Bundle")
        self.create_version_file()
        self.create_ping_file()
        self.create_pool_files()
        self.download_log_files()
