'''Utilities relating to creation and verification of Metronome jobs

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_jobs IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import json
import logging
import tempfile
import traceback

import retrying

import sdk_cmd
import shakedown

log = logging.getLogger(__name__)

CREATE_JOB_ENDPOINT = '/v1/jobs'
DELETE_JOB_ENDPOINT_TEMPLATE = '/v1/jobs/{}'
START_JOB_ENDPOINT_TEMPLATE = '/v1/jobs/{}/runs'
GET_JOB_RUN_ENDPOINT_TEMPLATE = '/v1/jobs/{}/runs/{}'


# --- Install/uninstall jobs to the cluster


def install_job(job_dict, tmp_dir=None):
    job_name = job_dict['id']
    log.info('Adding job {}:\n{}'.format(job_name, json.dumps(job_dict)))

    # attempt to delete current job, if any:
    _remove_job_by_name(job_name, retry=False)

    sdk_cmd.request(
        'POST',
        '{}{}'.format(shakedown.dcos_service_url('metronome'), CREATE_JOB_ENDPOINT),
        json=job_dict)


def remove_job(job_dict):
    _remove_job_by_name(job_dict['id'], retry=True)


def _remove_job_by_name(job_name, retry):
    try:
        sdk_cmd.request(
            'DELETE',
            '{}{}'.format(
                shakedown.dcos_service_url('metronome'),
                DELETE_JOB_ENDPOINT_TEMPLATE.format(job_name)),
            retry=retry)
    except:
        log.info('Failed to remove any existing job named {} (this is likely as expected): {}'.format(
            job_name, traceback.format_exc()))


class InstallJobContext(object):
    """Context manager for temporarily installing and removing metronome jobs."""

    def __init__(self, jobs):
        self.job_dicts = jobs

    def __enter__(self):
        tmp_dir = tempfile.mkdtemp(prefix='sdk-test')
        for j in self.job_dicts:
            install_job(j, tmp_dir=tmp_dir)

    def __exit__(self, *args):
        for j in self.job_dicts:
            remove_job(j)


# --- Run jobs and check their outcomes


def run_job(job_dict, timeout_seconds=600, raise_on_failure=True):
    job_name = job_dict['id']

    # start job run, get run ID to be polled against:
    run_id = sdk_cmd.request(
        'POST',
        '{}{}'.format(
            shakedown.dcos_service_url('metronome'),
            START_JOB_ENDPOINT_TEMPLATE.format(job_name)))['id']

    # wait for run to succeed, throw if run fails:
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: not res,
        retry_on_exception=lambda ex: False)
    def wait():
        # disregard metronome 404 errors, just try again
        try:
            run_status = sdk_cmd.request(
                'GET',
                '{}{}'.format(
                    shakedown.dcos_service_url('metronome'),
                    GET_JOB_RUN_ENDPOINT_TEMPLATE.format(job_name, run_id)),
                retry=False)['status']
        except:
            log.info(traceback.format_exc())
            return False

        log.info('Job {} run {} status: {}'.format(job_name, run_id, run_status))

        # note: if a job has restart.policy=ON_FAILURE, it may just go back to CREATING
        if raise_on_failure and run_status in ['FAILED', 'KILLED']:
            raise Exception('Job {} with id {} has failed, exiting early'.format(job_name, run_id))
        return run_status == 'FINISHED'

    wait()

    return run_id


class RunJobContext(object):
    """Context manager for running different named jobs at startup/shutdown."""

    def __init__(self, before_jobs=[], after_jobs=[], timeout_seconds=600):
        self.before_job_dicts = before_jobs
        self.after_job_dicts = after_jobs
        self.timeout_seconds = timeout_seconds

    def __enter__(self):
        for j in self.before_job_dicts:
            run_job(j, timeout_seconds=self.timeout_seconds)

    def __exit__(self, *args):
        for j in self.after_job_dicts:
            run_job(j, timeout_seconds=self.timeout_seconds)
