import json
import logging

import sdk_cmd

import config
from base_tech_bundle import BaseTechBundle

logger = logging.getLogger(__name__)


class KafkaBundle(BaseTechBundle):

    def __init__(self, package_name, service_name, scheduler_tasks, service, output_directory):
        super().__init__(package_name,
                         service_name,
                         scheduler_tasks,
                         service,
                         output_directory)

    def create(self):
        logger.info("Creating Kafka bundle")
        self.create_configuration_file()
        self.create_pod_status_file()
        self.create_plans_status_files()
        brokers = self.create_broker_list_file()
        if brokers:
            for broker_id in brokers:
                self.create_broker_get_file(broker_id)

    @config.retry
    def create_broker_list_file(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "broker list", print_output=False
        )

        if rc != 0 or stderr:
            logger.error(
                "Could not perform broker list\nstdout: '%s'\nstderr: '%s'", stdout, stderr
            )
        else:
            self.write_file("service_broker_list.json", stdout)
            return json.loads(stdout)

    @config.retry
    def create_broker_get_file(self, broker_id):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "broker get %s" % broker_id, print_output=False
        )

        if rc != 0 or stderr:
            logger.error(
                "Could not perform broker get %s\nstdout: '%s'\nstderr: '%s'", broker_id, stdout, stderr
            )
        else:
            self.write_file("service_broker_get_%s.json" % broker_id, stdout)
