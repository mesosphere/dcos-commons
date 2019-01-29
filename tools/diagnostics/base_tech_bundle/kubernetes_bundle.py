import logging

import sdk_cmd

from base_tech_bundle import BaseTechBundle
import config
import json

logging = logging.getLogger(__name__)


class KubernetesBundle(BaseTechBundle):
    def __init__(self, package_name, service_name, scheduler_tasks, service, output_directory):
        super().__init__(package_name,
                         service_name,
                         scheduler_tasks,
                         service,
                         output_directory)

    @config.retry
    def create_configuration_file(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "manager describe", print_output=False
        )

        if rc != 0 or stderr:
            logging.error(
                "Could not get service configuration\nstdout: '%s'\nstderr: '%s'", stdout, stderr
            )
        else:
            self.write_file("manager_service_configuration.json", stdout)

    @config.retry
    def create_pod_status_file(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "manager pod status --json", print_output=False
        )

        if rc != 0 or stderr:
            logging.error("Could not get pod status\nstdout: '%s'\nstderr: '%s'", stdout, stderr)
        else:
            self.write_file("manager_service_pod_status.json", stdout)

    @config.retry
    def create_plan_status_file(self, plan):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name,
            self.service_name,
            "manager plan status {} --json".format(plan),
            print_output=False,
        )

        if rc != 0 or stderr:
            logging.error("Could not get pod status\nstdout: '%s'\nstderr: '%s'", stdout, stderr)
        else:
            self.write_file("manager_service_plan_status_{}.json".format(plan), stdout)

    @config.retry
    def create_plans_status_files(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "manager plan list", print_output=False
        )

        if rc != 0 or stderr:
            logging.error("Could not get plan list\nstdout: '%s'\nstderr: '%s'", stdout, stderr)
        else:
            plans = json.loads(stdout)
            for plan in plans:
                self.create_plan_status_file(plan)

    @config.retry
    def create_cluster_list(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "cluster list", print_output=False
        )

        if rc != 0 or stderr:
            logging.error("Could not get cluster list\nstdout: '%s'\nstderr: '%s'", stdout, stderr)
        else:
            self.write_file("cluster_list.json", stdout)

    @config.retry
    def create_cluster_debug_state_properties(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "cluster debug state properties", print_output=False
        )

        if rc != 0 or stderr:
            logging.error("Could not get cluster state properties\nstdout: '%s'\nstderr: '%s'", stdout, stderr)
        else:
            self.write_file("cluster_debug_state_properties.json", stdout)

    @config.retry
    def create_cluster_debug_endpoints(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "cluster debug endpoints", print_output=False
        )

        if rc != 0 or stderr:
            logging.error("Could not get cluster debug endpoints\nstdout: '%s'\nstderr: '%s'", stdout, stderr)
        else:
            self.write_file("cluster_debug_endpoints.json", stdout)

    @config.retry
    def create_cluster_pod_status(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "cluster debug pod status --json", print_output=False
        )

        if rc != 0 or stderr:
            logging.error("Could not get cluster pod status\nstdout: '%s'\nstderr: '%s'", stdout, stderr)
        else:
            self.write_file("cluster_debug_pod_status.json", stdout)

    @config.retry
    def task_exec(self, task_id, cmd):
        pass

    def create(self):
        logging.info("Creating Kubernetes Bundle")
        self.create_configuration_file()
        self.create_plans_status_files()
        self.create_pod_status_file()
        self.create_cluster_list()
        self.create_cluster_debug_state_properties()
        self.create_cluster_pod_status()
