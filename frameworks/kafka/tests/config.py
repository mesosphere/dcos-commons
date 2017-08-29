PACKAGE_NAME = 'beta-kafka'
SERVICE_NAME = 'kafka'
DEFAULT_BROKER_COUNT = 3
DEFAULT_PARTITION_COUNT = 1
DEFAULT_REPLICATION_FACTOR = 1
DEFAULT_PLAN_NAME = 'deploy'
DEFAULT_PHASE_NAME = 'Deployment'
DEFAULT_POD_TYPE = 'kafka'
DEFAULT_TASK_NAME = 'broker'
DEFAULT_KAFKA_TIMEOUT = 10 * 60
DEFAULT_TOPIC_NAME = 'topic1'
EXPECTED_METRICS = [
    "kafka.network.RequestMetrics.ResponseQueueTimeMs.max",
    "kafka.socket-server-metrics.io-ratio",
    "kafka.controller.ControllerStats.LeaderElectionRateAndTimeMs.p95"
]
