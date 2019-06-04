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
            if "rlimit_info" in task_info["container"]:
                for rlimit in task_info["container"]["rlimit_info"]["rlimits"]:
                    if rlimit["type"] == limit_type:
                        result[task_info["id"]] = (rlimit["soft"], rlimit["hard"])
    return result


@pytest.mark.parametrize(
    "limit_value,limit_type",
    [
        ("stack", "RLMT_STACK"),
        ("memory", "RLMT_MEMLOCK"),
        ("processes", "RLMT_NPROC"),
    ],
)
def test_rlimts(limit_value, limit_type):
    tasks_to_rlimit = get_task_to_rlimits_mapping(limit_type)
    for (task, rlimit) in tasks_to_rlimit.items():
        exit_code, stdout, _ = sdk_cmd.run_cli('task exec {} bash -c "ps -o pid -C java | grep -v grep | grep -v PID | head -1"'.format(task))
        exit_code, stdout, _ = sdk_cmd.run_cli('task exec {} bash -c "cat /proc/{}/limits | grep {}"'.format(task, stdout, limit_value))
        stack_limit = re.sub(' +', ' ', stdout).split(' ')[-3:-1]
        assert rlimit[0] == int(stack_limit[0]) or int(stack_limit[0]) == 0
        assert rlimit[1] == int(stack_limit[1]) or int(stack_limit[1]) == 0
