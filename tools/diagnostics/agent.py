import logging
import os
import re
from typing import List

import sdk_cmd

import config

logger = logging.getLogger(__name__)


def is_http_server_error(http_status_code: int) -> bool:
    return http_status_code >= 500 and http_status_code <= 599


@config.retry
def debug_agent_files(agent_id: str) -> List[str]:
    response = sdk_cmd.cluster_request(
        "GET",
        "/slave/{}/files/debug".format(agent_id),
        retry=False,
        raise_on_error=False,
        log_response=False,
    )

    if is_http_server_error(response.status_code):
        # Retry.
        raise Exception(response)

    if not response.ok:
        # Retry.
        raise Exception(response)

    return response.json()


@config.retry
def browse_agent_path(agent_id: str, agent_path: str) -> List[dict]:
    response = sdk_cmd.cluster_request(
        "GET",
        "/slave/{}/files/browse?path={}".format(agent_id, agent_path),
        retry=False,
        raise_on_error=False,
        log_response=False,
    )

    if is_http_server_error(response.status_code):
        # Retry.
        raise Exception(response)

    if response.status_code == 404:
        return []

    if not response.ok:
        # Retry.
        raise Exception(response)

    return response.json()


def browse_executor_sandbox(agent_id: str, executor_sandbox_path: str) -> List[dict]:
    return browse_agent_path(agent_id, executor_sandbox_path)


def browse_executor_tasks(agent_id: str, executor_sandbox_path: str) -> List[dict]:
    return browse_executor_sandbox(agent_id, os.path.join(executor_sandbox_path, "tasks"))


def browse_task_sandbox(agent_id: str, executor_sandbox_path: str, task_id: str) -> List[dict]:
    executor_tasks = browse_executor_tasks(agent_id, executor_sandbox_path)

    if executor_tasks:
        task_sandbox_path = os.path.join(executor_sandbox_path, "tasks/{}/".format(task_id))
        return browse_agent_path(agent_id, task_sandbox_path)
    else:
        return []


@config.retry
def download_agent_path(
    agent_id: str, agent_file_path: str, output_file_path: str, chunk_size: int = 8192
) -> None:
    stream = sdk_cmd.cluster_request(
        "GET",
        "/slave/{}/files/download?path={}".format(agent_id, agent_file_path),
        retry=False,
        raise_on_error=False,
        log_response=False,
        stream=True,
    )

    if is_http_server_error(stream.status_code):
        raise Exception(stream)

    if stream.status_code == 404:
        return

    if not stream.ok:
        # Retry.
        raise Exception(stream)

    with open(output_file_path, "wb") as f:
        for chunk in stream.iter_content(chunk_size=chunk_size):
            f.write(chunk)


def download_sandbox_files(
    agent_id: str, sandbox: List[dict], output_base_path: str, patterns_to_download: List[str] = []
) -> List[dict]:
    if not os.path.exists(output_base_path):
        os.makedirs(output_base_path)

    for task_file in sandbox:
        task_file_basename = os.path.basename(task_file["path"])
        for pattern in patterns_to_download:
            if re.match(pattern, task_file_basename):
                download_agent_path(
                    agent_id, task_file["path"], os.path.join(output_base_path, task_file_basename)
                )


def download_task_files(
    agent_id: str,
    executor_sandbox_path: str,
    task_id: str,
    base_path: str,
    patterns_to_download: List[str] = [],
) -> List[dict]:
    executor_sandbox = browse_executor_sandbox(agent_id, executor_sandbox_path)
    pod_task_sandbox = browse_task_sandbox(agent_id, executor_sandbox_path, task_id)

    # Pod task: download files under its sandbox and also under its parent executor's sandbox.
    if pod_task_sandbox:
        output_pod_task_directory = os.path.join(base_path, task_id, "task")
        download_sandbox_files(
            agent_id, executor_sandbox, output_pod_task_directory, patterns_to_download
        )

        output_executor_directory = os.path.join(base_path, task_id, "executor")
        download_sandbox_files(
            agent_id, pod_task_sandbox, output_executor_directory, patterns_to_download
        )
    # Scheduler task: no parent executor, only download files under its sandbox.
    else:
        output_directory = os.path.join(base_path, task_id)
        download_sandbox_files(agent_id, executor_sandbox, output_directory, patterns_to_download)
