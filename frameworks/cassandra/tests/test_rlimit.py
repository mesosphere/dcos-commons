import json
import re
import pytest
import sdk_install
import sdk_cmd
from tests import config


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
        )

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


def get_task_to_rlimits_mapping(limit_type):
    result = {}
    exit_code, stdout, _ = sdk_cmd.run_cli("task --json")
    if exit_code != 0:
        return None

    tasks_info = json.loads(stdout)
    for task_info in tasks_info:
        if "container" in task_info:
            for rlimit in task_info["container"]["rlimit_info"]["rlimits"]:
                if rlimit["type"] == limit_type:
                    result[task_info["id"]] = (rlimit["soft"], rlimit["hard"])
    return result


def test_rlimt_stack():
    tasks_to_rlimit = get_task_to_rlimits_mapping("RLMT_STACK")
    for (task, rlimit) in tasks_to_rlimit.items():
        exit_code, stdout, _ = sdk_cmd.run_cli('task exec {} bash -c "ps -o pid -C java | grep -v grep | grep -v PID | head -1"'.format(task))
        exit_code, stdout, _ = sdk_cmd.run_cli('task exec {} bash -c "cat /proc/{}/limits | grep stack"'.format(task, stdout))
        stack_limit = re.sub(' +', ' ', stdout).split(' ')[-3:-1]
        assert rlimit[0] == int(stack_limit[0])
        assert rlimit[1] == int(stack_limit[1])


def test_rlimt_memlock():
    tasks_to_rlimit = get_task_to_rlimits_mapping("RLMT_MEMLOCK")
    for (task, rlimit) in tasks_to_rlimit.items():
        exit_code, stdout, _ = sdk_cmd.run_cli('task exec {} bash -c "ps -o pid -C java | grep -v grep | grep -v PID | head -1"'.format(task))
        exit_code, stdout, _ = sdk_cmd.run_cli('task exec {} bash -c "cat /proc/{}/limits | grep memory"'.format(task, stdout))
        stack_limit = re.sub(' +', ' ', stdout).split(' ')[-3:-1]
        assert rlimit[0] == int(stack_limit[0]) or int(stack_limit[0]) == 0
        assert rlimit[1] == int(stack_limit[1]) or int(stack_limit[0]) == 0


def test_rlimt_nproc():
    tasks_to_rlimit = get_task_to_rlimits_mapping("RLMT_NPROC")
    for (task, rlimit) in tasks_to_rlimit.items():
        exit_code, stdout, _ = sdk_cmd.run_cli('task exec {} bash -c "ps -o pid -C java | grep -v grep | grep -v PID | head -1"'.format(task))
        exit_code, stdout, _ = sdk_cmd.run_cli('task exec {} bash -c "cat /proc/{}/limits | grep processes"'.format(task, stdout))
        stack_limit = re.sub(' +', ' ', stdout).split(' ')[-3:-1]
        assert rlimit[0] == int(stack_limit[0])
        assert rlimit[1] == int(stack_limit[1])
