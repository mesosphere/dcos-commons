import pytest
import json

import shakedown
import sdk_cmd

from tests.config import (
    PACKAGE_NAME,
    SERVICE_NAME,
    DEFAULT_PARTITION_COUNT,
    DEFAULT_REPLICATION_FACTOR,
    DEFAULT_BROKER_COUNT,
    DEFAULT_PLAN_NAME,
    DEFAULT_PHASE_NAME,
    DEFAULT_POD_TYPE,
    DEFAULT_TASK_NAME
)


def service_cli(cmd_str, get_json=True, print_output=True, service_name=SERVICE_NAME):
    full_cmd = '{} --name={} {}'.format(PACKAGE_NAME, service_name, cmd_str)
    ret_str = sdk_cmd.run_cli(full_cmd, print_output=print_output)
    if get_json:
        return json.loads(ret_str)
    else:
        return ret_str


def broker_count_check(count, service_name=SERVICE_NAME):
    def fun():
        try:
            if len(service_cli('broker list', service_name=service_name)) == count:
                return True
        except:
            pass
        return False

    shakedown.wait_for(fun)
