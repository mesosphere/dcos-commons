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

        broker_list = None
        if rc != 0:
            logger.error(
                "Could not perform broker list. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'", rc, stdout, stderr
            )
        else:
            if stderr:
                logger.warning("Non-fatal broker list message\nstderr: '%s'", stderr)

            try:
                broker_list = json.loads(stdout)
            except Exception:
                logger.error(
                    "Could not parse broker list json. Writing to service_broker_list.json\n"
                    "stdout: '%s'\nstderr: '%s'", stdout, stderr
                )
            finally:
                self.write_file("service_broker_list.json", stdout)
        return broker_list

    @config.retry
    def create_broker_get_file(self, broker_id):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "broker get %s" % broker_id, print_output=False
        )

        if rc != 0:
            logger.error(
                "Could not perform broker get %s. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'", broker_id, rc, stdout, stderr
            )
        else:
            if stderr:
                logger.warning("Non-fatal broker get %s message\nstderr: '%s'", broker_id, stderr)
            self.write_file("service_broker_get_%s.json" % broker_id, stdout)
