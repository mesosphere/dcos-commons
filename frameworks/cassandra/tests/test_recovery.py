import pytest

from tests.config import *
import sdk_cmd as cmd
import sdk_install as install
import sdk_plan as plan
import sdk_spin as spin
import sdk_tasks as tasks
import sdk_utils as utils


VERIFY_REPLACE_JOB = 'verify-node-replace'


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, check_suppression=False)


def setup_function(function):
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


def try_job(job_name):
    job_name = qualified_job_name(job_name)

    run_id = launch_job(job_name)
    verify_job_finished(job_name, run_id)

    return run_id in [
        r['id'] for r in
        get_runs(job_name)['history']['successfulFinishedRuns']
    ]


@pytest.mark.sanity
def test_node_replace_replaces_node():
    tasks = cmd.run_cli('task')
    node_ip = [
        t for t in tasks.split('\n') if t.startswith('node-2-server')
    ].pop().split()[1]

    # start replace and wait for it to finish
    cmd.run_cli('cassandra pods replace node-2')
    plan.wait_for_completed_recovery(PACKAGE_NAME)

    # Install replace verification job with correct node IP templated
    # (the job checks for that IP's absence in the peers list and also verifies
    # that the expected number of peers is present, meaning that the node was
    # replaced from Cassandra's perspective)
    with JobContext([VERIFY_REPLACE_JOB], NODE_IP=node_ip):
        spin.time_wait_noisy(lambda: try_job(VERIFY_REPLACE_JOB))
