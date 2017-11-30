'''Utilities relating to creation and verification of Metronome jobs

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_jobs IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import json
import logging
import os
import re
import tempfile
import traceback

import shakedown

import sdk_cmd

log = logging.getLogger(__name__)


# --- Install/uninstall jobs to the cluster


def install_job(job_dict, tmp_dir=None):
    job_name = job_dict['id']

    if not tmp_dir:
        tmp_dir = tempfile.mkdtemp(prefix='sdk-test')
    out_filename = os.path.join(tmp_dir, '{}.json'.format(job_name))
    job_str = json.dumps(job_dict)
    log.info('Writing job file for {} to: {}\n{}'.format(job_name, out_filename, job_str))
    with open(out_filename, 'w') as f:
        f.write(job_str)

    _remove_job_by_name(job_name)
    sdk_cmd.run_cli('job add {}'.format(out_filename))


def remove_job(job_dict):
    _remove_job_by_name(job_dict['id'])


def _remove_job_by_name(job_name):
    try:
        # --stop-current-job-runs ensures that fail-looping jobs (with restart.policy=ON_FAILURE) are consistently removed.
        sdk_cmd.run_cli('job remove {} --stop-current-job-runs'.format(job_name), print_output=False)
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
    run_id = json.loads(sdk_cmd.run_cli('job run {} --json'.format(job_name)))['id']

    # wait for run to succeed, throw if run fails:
    def fun():
        # catch errors from CLI: ensure that the only error raised is our own:
        try:
            successful_runs = json.loads(sdk_cmd.run_cli(
                'job history --json {}'.format(job_name), print_output=False))
            failed_runs = json.loads(sdk_cmd.run_cli(
                'job history --failures --json {}'.format(job_name), print_output=False))
        except:
            log.info(traceback.format_exc())
            return False

        successful_ids = [r['id'] for r in successful_runs]
        failed_ids = [r['id'] for r in failed_runs]

        log.info('Job {} run history (waiting for successful {}): successful={} failed={}'.format(
            job_name, run_id, successful_ids, failed_ids))
        # note: if a job has restart.policy=ON_FAILURE, it won't show up in failed_ids if it fails
        if raise_on_failure and run_id in failed_ids:
            raise Exception('Job {} with id {} has failed, exiting early'.format(job_name, run_id))
        return run_id in successful_ids
    shakedown.wait_for(fun, noisy=True, timeout_seconds=timeout_seconds, ignore_exceptions=False)

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
