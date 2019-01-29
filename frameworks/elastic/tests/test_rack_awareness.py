import logging

import pytest
import sdk_fault_domain
import sdk_install
import sdk_utils
from tests import config
from toolz import get_in

log = logging.getLogger(__name__)


@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.11")
@sdk_utils.dcos_ee_only
def test_zones_not_referenced_in_placement_constraints():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME,
                        config.DEFAULT_TASK_COUNT)

    nodes_info = config.get_elasticsearch_nodes_info(
        service_name=config.SERVICE_NAME)

    for node_uid, node in nodes_info["nodes"].items():
        assert (
            get_in(
                ["settings", "cluster", "routing", "allocation", "awareness", "attributes"], node
            )
            is None
        )
        assert sdk_fault_domain.is_valid_zone(get_in(["attributes", "zone"], node))

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.11")
@sdk_utils.dcos_ee_only
def test_zones_referenced_in_placement_constraints():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_TASK_COUNT,
        additional_options={
            "master_nodes": {"placement": '[["@zone", "GROUP_BY"]]'},
            "data_nodes": {"placement": '[["@zone", "GROUP_BY"]]'},
            "ingest_nodes": {"placement": '[["@zone", "GROUP_BY"]]'},
            "coordinator_nodes": {"placement": '[["@zone", "GROUP_BY"]]'}, 
        },
        )

    nodes_info = config.get_elasticsearch_nodes_info(
        service_name=config.SERVICE_NAME)

    for node_uid, node in nodes_info["nodes"].items():
        assert "zone" == get_in(
            ["settings", "cluster", "routing", "allocation", "awareness", "attributes"], node
        )
        assert sdk_fault_domain.is_valid_zone(get_in(["attributes", "zone"], node))

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.11")
@sdk_utils.dcos_ee_only
def test_heterogeneus_zone_constraints():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_TASK_COUNT,
        additional_options={
            "master_nodes": {"placement": '[["@zone", "GROUP_BY"]]'},
            "data_nodes": {"placement": '[["hostname", "UNIQUE"]]'},
        },
    )

    document_id = 99
    document_fields = {"name": "X-Pack", "role": "commercial plugin"}
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        document_id,
        document_fields,
        service_name=config.SERVICE_NAME,
    )
    config.verify_document(config.SERVICE_NAME, document_id, document_fields)

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

