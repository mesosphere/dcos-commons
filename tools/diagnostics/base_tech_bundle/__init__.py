from service_bundle import ServiceBundle

import json
import logging
import config
import sdk_cmd

log = logging.getLogger(__name__)


class BaseTechBundle(ServiceBundle):

    def __init__(self, package_name, service_name, scheduler_tasks, service, output_directory):
        self.package_name = package_name
        self.service_name = service_name
        self.scheduler_tasks = scheduler_tasks
        self.service = service
        self.framework_id = service.get("id")
        self.output_directory = output_directory
        self.install_cli()

    @config.retry
    def install_cli(self):
        sdk_cmd.run_cli(
            "package install {} --cli --yes".format(self.package_name),
            print_output=False,
            check=True,
        )

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

    def task_exec(self):
        raise NotImplementedError

    def create(self):
        log.info("Creating base-tech bundle")
        self.create_configuration_file()
        self.create_pod_status_file()
        self.create_plans_status_files()


from .cassandra_bundle import CassandraBundle  # noqa: E402
from .elastic_bundle import ElasticBundle  # noqa: E402
from .hdfs_bundle import HdfsBundle  # noqa: E402
from .kafka_bundle import KafkaBundle  # noqa: E402
from .kubernetes_bundle import KubernetesBundle   # noqa: E402


BASE_TECH_BUNDLE = {
    "beta-cassandra": CassandraBundle,
    "beta-elastic": ElasticBundle,
    "beta-hdfs": HdfsBundle,
    "beta-kafka": KafkaBundle,
    "cassandra": CassandraBundle,
    "confluent-kafka": KafkaBundle,
    "elastic": ElasticBundle,
    "hdfs": HdfsBundle,
    "kafka": KafkaBundle,
    "kubernetes": KubernetesBundle,
}


SUPPORTED_PACKAGES = sorted(BASE_TECH_BUNDLE.keys())


def get_bundle_class(package_name: str):
    if package_name in SUPPORTED_PACKAGES:
        return BASE_TECH_BUNDLE.get(package_name)
    else:
        return BaseTechBundle


__all__ = [
    "BASE_TECH_BUNDLE",
    "BaseTechBundle",
    "CassandraBundle",
    "ElasticBundle",
    "HdfsBundle",
    "KafkaBundle",
]
