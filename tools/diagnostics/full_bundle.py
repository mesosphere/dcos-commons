from datetime import date, datetime
from typing import List
import json
import logging
import os

import sdk_cmd
import sdk_utils

from bundle import Bundle
from service_bundle import ServiceBundle
import base_tech_bundle as base_tech
import config

log = logging.getLogger(__name__)


@config.retry
def get_dcos_services() -> (bool, str):
    rc, stdout, stderr = sdk_cmd.run_cli(
        "service --completed --inactive --json", print_output=False
    )

    if rc != 0 or stderr:
        return (
            False,
            "Could not get services state\nstdout: '{}'\nstderr: '{}'".format(stdout, stderr),
        )
    else:
        return (True, stdout)


def to_dcos_service_name(service_name: str) -> str:
    """DC/OS service names don't contain the first slash.
    i.e.: |     SDK service name     |   DC/OS service name    |
          |--------------------------+-------------------------|
          | /data-services/cassandra | data-services/cassandra |
    """
    return service_name.lstrip("/")


def is_service_named(service_name: str, service: dict) -> bool:
    return service.get("name") == to_dcos_service_name(service_name)


def is_service_active(service: dict) -> bool:
    return service.get("active") is True


def services_with_name(service_name: str, services: List[dict]) -> List[dict]:
    return [s for s in services if is_service_named(service_name, s)]


def active_services_with_name(service_name: str, services: List[dict]) -> List[dict]:
    return [s for s in services if is_service_named(service_name, s) and is_service_active(s)]


def is_service_scheduler_task(package_name: str, service_name: str, task: dict) -> bool:
    labels = task.get("labels", [])
    dcos_package_name = next(
        iter([l.get("value") for l in labels if l.get("key") == "DCOS_PACKAGE_NAME"]), ""
    )
    dcos_service_name = next(
        iter([l.get("value") for l in labels if l.get("key") == "DCOS_SERVICE_NAME"]), ""
    )
    return dcos_package_name == package_name and dcos_service_name == to_dcos_service_name(
        service_name
    )


def directory_date_string() -> str:
    return date.strftime(datetime.utcnow(), "%Y%m%dT%H%M%SZ")


class FullBundle(Bundle):
    def __init__(self, package_name, service_name, bundles_directory):
        self.package_name = package_name
        self.service_name = service_name
        self.bundles_directory = bundles_directory
        self.output_directory = self._create_bundle_directory()

    def _configure_logging(self):
        """Configures logging to write script output to bundle output directory.
        """
        logging.basicConfig(
            format="%(asctime)-15s %(levelname)s %(message)s",
            level="INFO",
            handlers=[
                logging.FileHandler(os.path.join(self.output_directory, "script.log")),
                logging.StreamHandler(),
            ],
        )

    def _bundle_directory_name(self) -> str:
        _, cluster_name, _ = sdk_cmd.run_cli("config show cluster.name", print_output=False)
        return "{}_{}_{}".format(
            cluster_name,
            sdk_utils.get_deslashed_service_name(self.service_name),
            directory_date_string(),
        )

    def _create_bundle_directory(self) -> str:
        directory_name = os.path.join(self.bundles_directory, self._bundle_directory_name())

        if not os.path.exists(directory_name):
            log.info("Creating directory %s", directory_name)
            os.makedirs(directory_name)

        return directory_name

    def create(self) -> (int, "FullBundle"):
        self._configure_logging()

        success, all_services_or_error = get_dcos_services()

        if not success:
            log.error(all_services_or_error)
            return 1, self

        all_services = json.loads(all_services_or_error)

        self.write_file("dcos_services.json", all_services, serialize_to_json=True)

        # An SDK service might have multiple DC/OS service entries. We expect that at most one is
        # "active".
        services = [s for s in all_services if is_service_named(self.service_name, s)]
        # TODO: handle inactive services too.
        active_services = [s for s in services if is_service_active(s)]

        if not active_services:
            log.error("Could not find active service named '%s'", self.service_name)
            return 1, self

        if len(active_services) > 1:
            log.warn("More than one active service named '%s'", self.service_name)

        active_service = active_services[0]

        marathon_services = [s for s in all_services if is_service_named("marathon", s)]
        # TODO: handle the possibility of having no active Marathon services?
        if len(marathon_services) > 1:
            log.warn("More than one marathon services: %s", len(marathon_services))

        active_marathon_services = [s for s in marathon_services if is_service_active(s)]
        # TODO: handle the possibility of having more than one Marathon service?
        active_marathon_service = active_marathon_services[0]

        # TODO: search in "unreachable_tasks" too?
        scheduler_tasks = [
            t
            for t in active_marathon_service.get("tasks", [])
            if is_service_scheduler_task(self.package_name, self.service_name, t)
        ]

        ServiceBundle(
            self.package_name,
            self.service_name,
            scheduler_tasks,
            active_service,
            self.output_directory,
        ).create()

        if base_tech.is_package_supported(self.package_name):
            BaseTechBundle = base_tech.get_bundle_class(self.package_name)

            BaseTechBundle(
                self.package_name,
                self.service_name,
                scheduler_tasks,
                active_service,
                self.output_directory,
            ).create()
        else:
            log.info(
                "Don't know how to get base tech diagnostics for package '%s'", self.package_name
            )
            log.info(
                "Supported packages:\n%s",
                "\n".join(["- {}".format(k) for k in base_tech.SUPPORTED_PACKAGES]),
            )
            log.info("This is ok, we were still able to get DC/OS and service-level diagnostics")

        log.info("\nCreated %s", os.path.abspath(self.output_directory))

        return 0, self
