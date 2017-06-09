import json
import pytest

import shakedown

from tests.config import *
import sdk_install as install
import sdk_tasks as tasks
import sdk_utils as utils
import sdk_networks as networks

OVERLAY_OPTIONS = {'service':{'virtual_network':True}}


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, check_suppression=False, additional_options=OVERLAY_OPTIONS)
    install_cassandra_jobs()


def setup_function(function):
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)
    remove_cassandra_jobs()


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
def test_service_health():
    check_dcos_service_health()
    node_tasks = (
        "node-0-server",
        "node-1-server",
        "node-2-server",
    )
    for task in node_tasks:
        networks.check_task_network(task)


@pytest.mark.sanity
@pytest.mark.overlay
def test_write_read_delete_data():
    # Write data to Cassandra with a metronome job
    launch_and_verify_job(WRITE_DATA_JOB)
    # Verify that the data was written
    launch_and_verify_job(VERIFY_DATA_JOB)
    # Delete all keyspaces and tables with a metronome job
    launch_and_verify_job(DELETE_DATA_JOB)
    # Verify that the keyspaces and tables were deleted
    launch_and_verify_job(VERIFY_DELETION_JOB)
    

@pytest.mark.sanity
@pytest.mark.overlay
def test_endpoints():
    def get_endpoints(suffix, correct_length):
        endpoints, _, rc = shakedown.run_dcos_command("{} endpoints {}".format(PACKAGE_NAME, suffix))
        assert rc == 0, "Failed to get endpoints on overlay network"
        endpoints = json.loads(endpoints)
        assert len(endpoints) == correct_length, "Wrong number of endpoints, got {} should be {}"\
                                                 .format(len(endpoints), correct_length)
        return endpoints

    get_endpoints("", 1)
    endpoints = get_endpoints("node", 4)
    assert "address" in endpoints, "Endpoints missing address key"
    for address in endpoints["address"]:
        assert address.startswith("9."), "IP address {} is incorrect, should start with a 9".format(address)
        assert address.endswith(":9042"), "Port incorrect, should be 9042, got {}".format(address)

    assert "dns" in endpoints, "Endpoints missing DNS key"
    for dns in endpoints["dns"]:
        assert "autoip.dcos.thisdcos.directory" in dns, "DNS {} is incorrect should have autoip.dcos.thisdcos."\
                "directory".format(dns)


