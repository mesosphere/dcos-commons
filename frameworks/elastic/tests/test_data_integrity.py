import json
import logging
import time
from concurrent.futures import ThreadPoolExecutor, wait

import pytest
from tests import config

import sdk_cmd
import sdk_install
import sdk_plan
import sdk_utils

log = logging.getLogger(__name__)

REPLICAS_NUMBER = 1
SHARDS_NUMBER = 3
DATA_NODES_NUMBER = 3
ALL_NODES_NUMBER = 7

DOCS_NUMBER = 1000
INTERVALS_NUMBER = 10
INDEXER_INTERVAL_IN_SECONDS = 10

package_name = config.PACKAGE_NAME
service_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)

index_name = "random_index"
index_type = "random_docs"
index_settings = {
    "settings": {
        "index.unassigned.node_left.delayed_timeout": "0",
        "number_of_shards": SHARDS_NUMBER,
        "number_of_replicas": REPLICAS_NUMBER,
    }}


@pytest.fixture(scope="module", autouse=True)
def init_service() -> None:
    try:
        sdk_install.uninstall(package_name, service_name)
        sdk_install.install(
            package_name,
            service_name,
            config.DEFAULT_TASK_COUNT,
            additional_options={
                "data_nodes": {"count": DATA_NODES_NUMBER},

            })
        yield
    finally:
        sdk_install.uninstall(package_name, service_name)


@pytest.fixture(autouse=True)
def init_index() -> None:
    try:
        config.create_index(index_name, index_settings, service_name=service_name)
        yield
    finally:
        config.delete_index(index_name, service_name=service_name)


@pytest.mark.sanity
def test_on_cluster_sync_write() -> None:
    _post_docs_with_bulk_request(DOCS_NUMBER)
    _assert_indexed_docs_number(DOCS_NUMBER)
    _replace_data_nodes_and_wait_for_completed_recovery(DATA_NODES_NUMBER)
    _assert_indexed_docs_number(DOCS_NUMBER)


@pytest.mark.sanity
def test_on_cluster_parallel_write() -> None:
    _post_docs_with_bulk_request(DOCS_NUMBER)
    _assert_indexed_docs_number(DOCS_NUMBER)

    try:
        with ThreadPoolExecutor(max_workers=2) as executor:
            n = DOCS_NUMBER // INTERVALS_NUMBER
            indexer_future = executor.submit(_interval_post_docs_with_bulk_request, n, INTERVALS_NUMBER,
                                             INDEXER_INTERVAL_IN_SECONDS)
            replace_future = executor.submit(_replace_data_nodes_and_wait_for_completed_recovery, DATA_NODES_NUMBER)

            wait([indexer_future, replace_future], timeout=2 * n * INDEXER_INTERVAL_IN_SECONDS)
    except:
        log.error("Failed to post docs or replace pods in parallel.")

    _assert_indexed_docs_number(2 * DOCS_NUMBER)


def _generate_elastic_docs_as_ndjson(docs_number: int) -> str:
    docs = []
    for i in range(docs_number):
        docs.append({"index": {}})
        docs.append({i: i * i})

    return '\n'.join(json.dumps(doc) for doc in docs) + '\n'


def _post_docs_with_bulk_request(docs_number: int) -> None:
    bulk_response = config._curl_query(
        service_name,
        "POST",
        "{}/{}/_bulk?refresh=wait_for".format(index_name, index_type),
        _generate_elastic_docs_as_ndjson(docs_number))
    assert bulk_response["errors"] == False


def _interval_post_docs_with_bulk_request(n: int, interval_numbers: int, interval_time: int) -> None:
    for _ in range(interval_numbers):
        _post_docs_with_bulk_request(n)
        time.sleep(interval_time)


def _assert_indexed_docs_number(docs_number: int) -> None:
    count_response = config._curl_query(service_name, "GET", "{}/{}/_count".format(index_name, index_type))
    assert count_response["count"] == docs_number


def _replace_data_nodes_and_wait_for_completed_recovery(nodes: int) -> None:
    for d in range(nodes):
        sdk_cmd.svc_cli(package_name, service_name, "pod replace data-{}".format(d))
        sdk_plan.wait_for_completed_recovery(service_name)
        config.wait_for_expected_nodes_to_exist(service_name=service_name, task_count=ALL_NODES_NUMBER)
