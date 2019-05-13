"""Utilities relating to creation and verification of Metronome jobs

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_jobs IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import json
import logging
from typing import Any, Dict, List

import retrying

import sdk_cmd

log = logging.getLogger(__name__)


# --- Install/uninstall jobs to the cluster


def install_job(job_dict: Dict[str, Any]) -> None:
    job_name = job_dict["id"]

    # attempt to delete current job, if any:
    _remove_job_by_name(job_name)

    log.info("Adding job {}:\n{}".format(job_name, json.dumps(job_dict)))
    sdk_cmd.service_request("POST", "metronome", "/v1/jobs", json=job_dict)


def remove_job(job_dict: Dict[str, Any]) -> None:
    _remove_job_by_name(job_dict["id"])


def _remove_job_by_name(job_name: str) -> None:
    try:
        # Metronome doesn't understand 'True' -- only 'true' will do.
        sdk_cmd.service_request(
            "DELETE",
            "metronome",
            "/v1/jobs/{}".format(job_name),
            retry=False,
            params={"stopCurrentJobRuns": "true"},
        )
    except Exception as e:
        log.info(
            "Failed to remove any existing job named {} (this is likely as expected):\n{}".format(
                job_name, e
            )
        )


class InstallJobContext(object):
    """Context manager for temporarily installing and removing metronome jobs."""

    def __init__(self, jobs: List[Dict[str, Any]]) -> None:
        self.job_dicts = jobs

    def __enter__(self) -> None:
        for j in self.job_dicts:
            install_job(j)

    def __exit__(self, *args: Any) -> None:
        for j in self.job_dicts:
            remove_job(j)


# --- Run jobs and check their outcomes


def run_job(
    job_dict: Dict[str, Any],
    timeout_seconds: int = 600,
    raise_on_failure: bool = True,
) -> str:
    job_name = job_dict["id"]

    # Start job run, get run ID to poll against:
    run_id = sdk_cmd.service_request(
        "POST", "metronome", "/v1/jobs/{}/runs".format(job_name), log_args=False
    ).json()["id"]
    assert isinstance(run_id, str)
    log.info("Started job {}: run id {}".format(job_name, run_id))

    # Wait for run to succeed, throw if run fails:
    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def wait() -> bool:
        # Note: We COULD directly query the run here via /v1/jobs/<job_name>/runs/<run_id>, but that
        # only works for active runs -- for whatever reason the run will disappear after it's done.
        # Therefore we have to query the full run history from the parent job and find our run_id there.
        run_history = sdk_cmd.service_request(
            "GET",
            "metronome",
            "/v1/jobs/{}".format(job_name),
            retry=False,
            params={"embed": "history"},
        ).json()["history"]

        successful_run_ids = [run["id"] for run in run_history["successfulFinishedRuns"]]
        failed_run_ids = [run["id"] for run in run_history["failedFinishedRuns"]]

        log.info(
            "Job {} run history (waiting for successful {}): successful={} failed={}".format(
                job_name, run_id, successful_run_ids, failed_run_ids
            )
        )

        # Note: If a job has restart.policy=ON_FAILURE, it won't show up in failed_run_ids even when it fails.
        #       Instead it will just keep restarting automatically until it succeeds or is deleted.
        if raise_on_failure and run_id in failed_run_ids:
            raise Exception("Job {} with id {} has failed, exiting early".format(job_name, run_id))

        return run_id in successful_run_ids

    wait()

    return run_id


class RunJobContext(object):
    """Context manager for running different named jobs at startup/shutdown."""

    def __init__(
        self,
        before_jobs: List[Dict[str, Any]] = [],
        after_jobs: List[Dict[str, Any]] = [],
        timeout_seconds: int = 600,
    ):
        self.before_job_dicts = before_jobs
        self.after_job_dicts = after_jobs
        self.timeout_seconds = timeout_seconds

    def __enter__(self) -> None:
        for j in self.before_job_dicts:
            run_job(j, timeout_seconds=self.timeout_seconds)

    def __exit__(self, *args: Any) -> None:
        for j in self.after_job_dicts:
            run_job(j, timeout_seconds=self.timeout_seconds)
