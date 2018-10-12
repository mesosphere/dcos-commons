import logging

from base_tech_bundle import BaseTechBundle

logger = logging.getLogger(__name__)


class KafkaBundle(BaseTechBundle):
    def create(self):
        logger.info("Creating Kafka bundle (noop)")
