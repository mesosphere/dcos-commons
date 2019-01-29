import logging

from base_tech_bundle import BaseTechBundle

logger = logging.getLogger(__name__)


class HdfsBundle(BaseTechBundle):

     def __init__(self, package_name, service_name, scheduler_tasks, service, output_directory):
        super().__init__(self,
                         package_name,
                         service_name,
                         scheduler_tasks,
                         service,
                         output_directory)

    def create(self):
        logger.info("Creating HDFS bundle (noop)")
