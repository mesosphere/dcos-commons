import logging

import pytest
import sdk_install
import sdk_fault_domain
import sdk_networks
import shakedown
from tests import config

log = logging.getLogger(__name__)

@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS)
        yield
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_allocation_awareness_attributes():
    nodes_info = config.get_elasticsearch_nodes_info(service_name=config.SERVICE_NAME)

    for node_uid, node in nodes_info["nodes"].items():
        assert "region,zone" == node["settings"]["cluster"]["routing"]["allocation"]["awareness"]["attributes"]
        assert sdk_fault_domain.is_valid_region(node["attributes"]["region"])
        assert sdk_fault_domain.is_valid_zone(node["attributes"]["region"], node["attributes"]["zone"])
