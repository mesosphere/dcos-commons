import logging

import sdk_cmd

import config
from base_tech_bundle import BaseTechBundle

logger = logging.getLogger(__name__)


class KafkaBundle(BaseTechBundle):
    def create(self):
        logger.info("Creating Kafka bundle")
        self.create_broker_list_file()

    @config.retry
    def create_broker_list_file(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "broker list", print_output=False
        )

        if rc != 0 or stderr:
            logger.error(
                "Could not get broker list\nstdout: '%s'\nstderr: '%s'", stdout, stderr
            )
        else:
            self.write_file("service_broker_list.json", stdout)
