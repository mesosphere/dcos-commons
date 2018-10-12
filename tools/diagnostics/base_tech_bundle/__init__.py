from service_bundle import ServiceBundle


class BaseTechBundle(ServiceBundle):
    def task_exec(self):
        raise NotImplementedError

    def create(self):
        raise NotImplementedError


from .cassandra_bundle import CassandraBundle  # noqa: E402
from .elastic_bundle import ElasticBundle  # noqa: E402
from .hdfs_bundle import HdfsBundle  # noqa: E402
from .kafka_bundle import KafkaBundle  # noqa: E402


BASE_TECH_BUNDLE = {
    "beta-cassandra": CassandraBundle,
    "beta-elastic": ElasticBundle,
    "beta-hdfs": HdfsBundle,
    "beta-kafka": KafkaBundle,
    "cassandra": CassandraBundle,
    "elastic": ElasticBundle,
    "hdfs": HdfsBundle,
    "kafka": KafkaBundle,
}


SUPPORTED_PACKAGES = sorted(BASE_TECH_BUNDLE.keys())


def get_bundle_class(package_name: str):
    return BASE_TECH_BUNDLE.get(package_name)


def is_package_supported(package_name: str):
    return package_name in SUPPORTED_PACKAGES


__all__ = [
    "BASE_TECH_BUNDLE",
    "BaseTechBundle",
    "CassandraBundle",
    "ElasticBundle",
    "HdfsBundle",
    "KafkaBundle",
]
