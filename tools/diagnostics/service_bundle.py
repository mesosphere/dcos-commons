import functools
import json
import logging
import os
import warnings
from toolz import groupby
from typing import List

import sdk_cmd
import sdk_diag

from bundle import Bundle
import agent
import config

log = logging.getLogger(__name__)


class ServiceBundle(Bundle):
    DOWNLOAD_FILES_WITH_PATTERNS = ["^stdout(\\.\\d+)?$", "^stderr(\\.\\d+)?$"]

    def __init__(self, package_name, service_name, scheduler_tasks, service, output_directory):
        self.package_name = package_name
        self.service_name = service_name
        self.scheduler_tasks = scheduler_tasks
        self.service = service
        self.framework_id = service.get("id")
        self.output_directory = output_directory

    @config.retry
    def install_cli(self):
        sdk_cmd.run_cli(
            "package install {} --cli --yes".format(self.package_name),
            print_output=False,
            check=True,
        )

    def tasks(self):
        return self.service.get("tasks") + self.service.get("completed_tasks")

    def tasks_with_state(self, state):
        return list(filter(lambda task: task["state"] == state, self.tasks()))

    def running_tasks(self):
        return self.tasks_with_state("TASK_RUNNING")

    def run_on_tasks(self, fn, task_ids):
        for task_id in task_ids:
            fn(task_id)

    def for_each_running_task_with_prefix(self, prefix, fn):
        task_ids = [t["id"] for t in self.running_tasks() if t["name"].startswith(prefix)]
        self.run_on_tasks(fn, task_ids)

    @config.retry
    def create_configuration_file(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "describe", print_output=False
        )

        if rc != 0 or stderr:
            log.error(
                "Could not get service configuration\nstdout: '%s'\nstderr: '%s'", stdout, stderr
            )
        else:
            self.write_file("service_configuration.json", stdout)

    @config.retry
    def create_pod_status_file(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "pod status --json", print_output=False
        )

        if rc != 0 or stderr:
            log.error("Could not get pod status\nstdout: '%s'\nstderr: '%s'", stdout, stderr)
        else:
            self.write_file("service_pod_status.json", stdout)

    @config.retry
    def create_plan_status_file(self, plan):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name,
            self.service_name,
            "plan status {} --json".format(plan),
            print_output=False,
        )

        if rc != 0 or stderr:
            log.error("Could not get pod status\nstdout: '%s'\nstderr: '%s'", stdout, stderr)
        else:
            self.write_file("service_plan_status_{}.json".format(plan), stdout)

    @config.retry
    def create_plans_status_files(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "plan list", print_output=False
        )

        if rc != 0 or stderr:
            log.error("Could not get plan list\nstdout: '%s'\nstderr: '%s'", stdout, stderr)
        else:
            plans = json.loads(stdout)
            for plan in plans:
                self.create_plan_status_file(plan)

    def download_log_files(self):
        all_tasks = self.scheduler_tasks + self.tasks()

        tasks_by_agent_id = dict(groupby("slave_id", all_tasks))

        agent_id_by_task_id = dict(map(lambda task: (task["id"], task["slave_id"]), all_tasks))

        agent_executor_paths = {}
        for agent_id in tasks_by_agent_id.keys():
            agent_executor_paths[agent_id] = agent.debug_agent_files(agent_id)

        task_executor_sandbox_paths = {}
        for agent_id, tasks in tasks_by_agent_id.items():
            for task in tasks:
                task_executor_sandbox_path = sdk_diag._find_matching_executor_path(
                    agent_executor_paths[agent_id], sdk_diag._TaskEntry(task)
                )
                if task_executor_sandbox_path:
                    task_executor_sandbox_paths[task["id"]] = task_executor_sandbox_path
                else:
                    log.info(
                        "Could not find executor sandbox path for task '%s'. This probably means that its agent ('%s') is missing",
                        task["id"],
                        task["slave_id"],
                    )

        for task_id, task_executor_sandbox_path in task_executor_sandbox_paths.items():
            agent_id = agent_id_by_task_id[task_id]

            if task_executor_sandbox_path:
                agent.download_task_files(
                    agent_id,
                    task_executor_sandbox_path,
                    task_id,
                    os.path.join(self.output_directory, "tasks"),
                    self.DOWNLOAD_FILES_WITH_PATTERNS,
                )
            else:
                log.warn(
                    "Could not find executor sandbox path in agent '%s' for task '%s'",
                    agent_id,
                    task_id,
                )

    @config.retry
    def create_offers_file(self):
        warnings.warn("The v1/debug/offers endpoint will be deprecated in favour of the newer "
                      "v2/debug/offers endpoint.", PendingDeprecationWarning)
        response = sdk_cmd.service_request("GET", self.service_name, "/v1/debug/offers",
                                           raise_on_error=False)
        if not response.ok:
            log.error(
                "Could not get scheduler offers\nstatus_code: '%s'\nstderr: '%s'",
                response.status_code, response.text
            )
        else:
            self.write_file("service_v1_debug_offers.html", response.text)

    @config.retry
    def create_v2_offers_file(self):
        response = sdk_cmd.service_request("GET", self.service_name, "/v2/debug/offers",
                                           raise_on_error=False)
        if not response.ok:
            log.error(
                "Could not get v2 scheduler offers\nstatus_code: '%s'\nstderr: '%s'",
                response.status_code, response.text
            )
        else:
            self.write_file("service_v2_debug_offers.json", response.text)

    @config.retry
    def create_plans_file(self):
        response = sdk_cmd.service_request("GET", self.service_name, "/v1/debug/plans",
                                           raise_on_error=False)
        if not response.ok:
            log.error(
                "Could not get scheduler plans\nstatus_code: '%s'\nstderr: '%s'",
                response.status_code, response.text
            )
        else:
            self.write_file("service_v1_debug_plans.json", response.text)

    @config.retry
    def create_taskstatuses_file(self):
        response = sdk_cmd.service_request("GET", self.service_name, "/v1/debug/taskStatuses",
                                           raise_on_error=False)
        if not response.ok:
            log.error(
                "Could not get scheduler task-statuses\nstatus_code: '%s'\nstderr: '%s'",
                response.status_code, response.text
            )
        else:
            self.write_file("service_v1_debug_taskStatuses.json", response.text)

    @functools.lru_cache()
    @config.retry
    def configuration_ids(self) -> List[str]:
        response = sdk_cmd.service_request("GET", self.service_name, "/v1/configurations",
                                           raise_on_error=False)
        if not response.ok:
            log.error(
                "Could not get scheduler configurations\nstatus_code: '%s'\nstderr: '%s'",
                response.status_code, response.text
            )
        else:
            return json.loads(response.text)

    @functools.lru_cache()
    @config.retry
    def configuration(self, configuration_id) -> dict:
        response = sdk_cmd.service_request("GET", self.service_name,
                                           "/v1/configurations/{}".format(configuration_id),
                                           raise_on_error=False)
        if not response.ok:
            log.error("Could not get scheduler configuration with ID '%s'"
                      "\nstatus_code: '%s'\nstderr: '%s'",
                      configuration_id, response.status_code, response.text
                      )
        else:
            return json.loads(response.text)

    @config.retry
    def create_configuration_ids_file(self):
        self.write_file(
            "service_v1_configuration_ids.json", self.configuration_ids(), serialize_to_json=True
        )

    @config.retry
    def create_configuration_files(self):
        for configuration_id in self.configuration_ids():
            self.write_file(
                "service_v1_configuration_{}.json".format(configuration_id),
                self.configuration(configuration_id),
                serialize_to_json=True,
            )

    def create(self):
        self.install_cli()
        self.create_configuration_file()
        self.create_pod_status_file()
        self.create_plans_status_files()
        self.create_offers_file()
        self.create_v2_offers_file()
        self.create_plans_file()
        self.create_taskstatuses_file()
        self.create_configuration_ids_file()
        self.create_configuration_files()
        self.download_log_files()
