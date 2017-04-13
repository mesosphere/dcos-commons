import pytest

from tests.config import *
import sdk_install as install
import sdk_plan as plan
import sdk_spin as spin
import sdk_tasks as tasks
import sdk_utils as utils


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, check_suppression=False)

    install_cassandra_jobs()
    # Write data to Cassandra with a metronome job, then verify it was written
    launch_and_verify_job(WRITE_DATA_JOB)
    launch_and_verify_job(VERIFY_DATA_JOB)


def setup_function(function):
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)

    # Delete Cassandra data, then remove job definitions from metronome
    launch_and_verify_job(DELETE_DATA_JOB)
    launch_and_verify_job(VERIFY_DELETION_JOB)
    remove_cassandra_jobs()


def test_cleanup_plan_completes():
    cleanup_parameters = {'CASSANDRA_KEYSPACE': 'testspace1'}

    plan.start_plan(PACKAGE_NAME, 'cleanup', parameters=cleanup_parameters)
    spin.time_wait_noisy(
        lambda: (
            plan.get_plan(PACKAGE_NAME, 'cleanup').json()['status'] ==
            'COMPLETE'
        )
    )


def test_repair_plan_completes():
    repair_parameters = {'CASSANDRA_KEYSPACE': 'testspace1'}

    plan.start_plan(PACKAGE_NAME, 'repair', parameters=repair_parameters)
    spin.time_wait_noisy(
        lambda: (
            plan.get_plan(PACKAGE_NAME, 'repair').json()['status'] ==
            'COMPLETE'
        )
    )


def test_upgrade_sstables_plan_completes():
    upgrade_sstables_parameters = {'CASSANDRA_KEYSPACE': 'testspace1'}

    plan.start_plan(PACKAGE_NAME, 'upgradesstables', parameters=upgrade_sstables_parameters)
    spin.time_wait_noisy(
        lambda: (
            plan.get_plan(PACKAGE_NAME, 'upgradesstables').json()['status'] ==
            'COMPLETE'
        )
    )
