import logging

import pytest
import sdk_fault_domain
import sdk_install
from sdk_utils import get_in
from tests import config

log = logging.getLogger(__name__)


@pytest.mark.sanity
def test_allocation_awareness_attributes_default_installation():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)

    nodes_info = config.get_elasticsearch_nodes_info(service_name=config.SERVICE_NAME)

    for node_uid, node in nodes_info["nodes"].items():
        assert "region,zone" == get_in(
            ["settings", "cluster", "routing", "allocation", "awareness", "attributes"],
            node)
        assert sdk_fault_domain.is_valid_region(get_in(["attributes", "region"], node))
        assert sdk_fault_domain.is_valid_zone(
            get_in(["attributes", "region"], node),
            get_in(["attributes", "zone"], node))

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


def test_allocation_awareness_attributes_disabled():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_TASK_COUNT,
        additional_options={"elasticsearch": {"shard_allocation_awareness_enabled": False}})

    nodes_info = config.get_elasticsearch_nodes_info(service_name=config.SERVICE_NAME)

    for node_uid, node in nodes_info["nodes"].items():
        assert None == get_in(
            ["settings", "cluster", "routing", "allocation", "awareness", "attributes"],
            node)
        assert None == get_in(["attributes", "region"], node)
        assert None == get_in(["attributes", "zone"], node)

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
