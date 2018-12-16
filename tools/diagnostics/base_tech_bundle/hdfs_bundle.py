import logging

from base_tech_bundle import BaseTechBundle

logger = logging.getLogger(__name__)


class HdfsBundle(BaseTechBundle):
    def create(self):
        logger.info("Creating HDFS bundle (noop)")
