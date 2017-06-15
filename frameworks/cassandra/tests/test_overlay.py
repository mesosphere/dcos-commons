import json
import tempfile
import pytest

import shakedown

from tests.config import *
from tests.test_plans import (
    test_read_write_delete_data,
    test_cleanup_plan_completes,
    test_repair_plan_completes)


import sdk_install as install
import sdk_tasks as tasks
import sdk_plan as plan
import sdk_jobs as jobs
import sdk_utils as utils
import sdk_networks as networks


OVERLAY_OPTIONS = {'service':{'virtual_network':True}}


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, check_suppression=False,
                    additional_options=OVERLAY_OPTIONS)
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    tmp_dir = tempfile.mkdtemp(prefix='cassandra-test')
    for job in TEST_JOBS:
        jobs.install_job(job, tmp_dir=tmp_dir)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)

    for job in TEST_JOBS:
        jobs.remove_job(job)



@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
def test_service_health():
    shakedown.service_healthy(PACKAGE_NAME)
    node_tasks = (
        "node-0-server",
        "node-1-server",
        "node-2-server",
    )
    for task in node_tasks:
        networks.check_task_network(task)


@pytest.mark.smoke
@pytest.mark.overlay
def test_basic_functionality():
    test_read_write_delete_data()


@pytest.mark.sanity
@pytest.mark.overlay
def test_functionality():
    test_read_write_delete_data()
    test_cleanup_plan_completes()
    test_repair_plan_completes()


@pytest.mark.sanity
@pytest.mark.overlay
def test_endpoints():
    def get_endpoints(endpoint_to_get, correct_length):
        endpoints, _, rc = shakedown.run_dcos_command("{} endpoints {}".format(PACKAGE_NAME, endpoint_to_get))
        assert rc == 0, "Failed to get endpoints on overlay network"
        endpoints = json.loads(endpoints)
        assert len(endpoints) == correct_length, "Wrong number of endpoints, got {} should be {}"\
                                                 .format(len(endpoints), correct_length)
        return endpoints

    endpoints = get_endpoints("", 1)  # tests that the correct number of endpoints are found, should just be "node"
    assert "node" in endpoints, "Cassandra endpoints should contain only 'node', got {}".format(endpoints)
    endpoints = get_endpoints("node", 4)
    assert "address" in endpoints, "Endpoints missing address key"

    ip_addresses = [e.split(":")[0] for e in endpoints["address"]]
    assert len(set(ip_addresses).intersection(set(shakedown.get_agents()))) == 0

    for address in endpoints["address"]:
        assert address.startswith("9."), "IP address {} is incorrect, should start with a 9".format(address)
        assert address.endswith(":9042"), "Port incorrect, should be 9042, got {}".format(address)

    assert "dns" in endpoints, "Endpoints missing DNS key"
    for dns in endpoints["dns"]:
        assert "autoip.dcos.thisdcos.directory" in dns, "DNS {} is incorrect should have autoip.dcos.thisdcos."\
                "directory".format(dns)


