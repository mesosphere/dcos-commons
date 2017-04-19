import json
import os

import shakedown

import sdk_cmd as cmd
import sdk_spin as spin


PACKAGE_NAME = 'cassandra'
DEFAULT_TASK_COUNT = 3
WAIT_TIME_IN_SECONDS = 360
DCOS_URL = shakedown.run_dcos_command('config show core.dcos_url')[0].strip()
DCOS_TOKEN = shakedown.run_dcos_command('config show core.dcos_acs_token')[0].strip()

TASK_RUNNING_STATE = 'TASK_RUNNING'

REQUEST_HEADERS = {
    'authorization': 'token=%s' % DCOS_TOKEN
}

WRITE_DATA_JOB = 'write-data'
VERIFY_DATA_JOB = 'verify-data'
DELETE_DATA_JOB = 'delete-data'
VERIFY_DELETION_JOB = 'verify-deletion'
TEST_JOBS = (
    WRITE_DATA_JOB, VERIFY_DATA_JOB, DELETE_DATA_JOB, VERIFY_DELETION_JOB
)


def check_dcos_service_health():
    return shakedown.service_healthy(PACKAGE_NAME)


def qualified_job_name(job_name):
    return 'test.cassandra.{}'.format(job_name)


def install_cassandra_jobs():
    jobs_folder = os.path.join(
        os.path.dirname(os.path.realpath(__file__)), 'jobs'
    )
    for job in TEST_JOBS:
        cmd.run_cli('job add {}'.format(
            os.path.join(jobs_folder, '{}.json'.format(job))
        ))


def launch_and_verify_job(job_name):
    job_name = qualified_job_name(job_name)

    cmd.run_cli('job run {}'.format(job_name))
    # Get the id of the run we just initiated
    run_id = [
        r['id'] for r in
        json.loads(cmd.run_cli('job show runs --json {}'.format(job_name)))
        if r['jobId'] == job_name
    ].pop()

    # Verify that our most recent run succeeded
    spin.time_wait_noisy(lambda: (
        run_id in [
            r['id'] for r in
            json.loads(cmd.run_cli(
                'job history --show-failures --json {}'.format(job_name)
            ))['history']['successfulFinishedRuns']
        ]
    ))


def remove_cassandra_jobs():
    for job in TEST_JOBS:
        cmd.run_cli('job remove {}'.format(qualified_job_name(job)))
