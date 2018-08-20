import logging

from diagnostics.base_tech_bundle import BaseTechBundle

logger = logging.getLogger(__name__)

DEFAULT_RETRY_WAIT = 1000
DEFAULT_RETRY_MAX_ATTEMPTS = 5


class KafkaBundle(BaseTechBundle):
    def create(self):
        logger.info("Creating Kafka bundle (noop)")
