import logging

from base_tech_bundle import BaseTechBundle

logger = logging.getLogger(__name__)


class HdfsBundle(BaseTechBundle):

    def __init__(self, package_name, service_name, scheduler_tasks, service, output_directory):
        super().__init__(package_name,
                         service_name,
                         scheduler_tasks,
                         service,
                         output_directory)

    def create(self):
        self.create_configuration_file()
        self.create_pod_status_file()
        self.create_plans_status_files()
        logger.info("Creating HDFS bundle (noop)")
