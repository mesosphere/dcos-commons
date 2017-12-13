import datetime

import pytest
import os
import re
import time
import json

import sdk_cmd
import sdk_install
import sdk_plan
import sdk_tasks
import sdk_utils

from tests import config

BROKER_KILL_GRACE_PERIOD = int(os.environ.get('BROKER_KILL_GRACE_PERIOD', 30))
EXPECTED_KAFKA_STARTUP_SECONDS = os.environ.get('KAFKA_EXPECTED_STARTUP', 30)
EXPECTED_DCOS_STARTUP_SECONDS = os.environ.get('DCOS_EXPECTED_STARTUP', 30)
STARTUP_POLL_DELAY_SECONDS = os.environ.get('STARTUP_LOG_POLL_DELAY', 2)

def setup_module(module):
    options = {
        "brokers": {
            "kill_grace_period": BROKER_KILL_GRACE_PERIOD
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    config.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_BROKER_COUNT,
        additional_options=options)
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)


def teardown_module(module):
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.availability
@pytest.mark.soak_availability
@pytest.mark.dcos_min_version('1.9')
def test_service_startup_rapid():
    max_restart_seconds = EXPECTED_KAFKA_STARTUP_SECONDS
    startup_padding_seconds = EXPECTED_DCOS_STARTUP_SECONDS
    retry_delay_seconds = STARTUP_POLL_DELAY_SECONDS

    task_short_name = 'kafka-0'
    broker_task_id_0 = sdk_tasks.get_task_ids(config.SERVICE_NAME, task_short_name)[0]

    # the following 'dcos kafka topic ....' command has expected output as follows:
    # 'Output: 100 records sent ....'
    # but may fail, i.e. have output such as follows:
    # '...leader not available...'
    stdout = ''
    retries = 15
    while retries > 0:
        retries -= 1
        stdout = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'topic producer_test test 100')
        if 'records sent' in stdout:
            break

    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod restart {}'.format(task_short_name), json=True)
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == task_short_name
    assert jsonobj['tasks'] == [ '{}-broker'.format(task_short_name) ]

    starting_fallback_time = datetime.datetime.now()

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, '{}-'.format(config.DEFAULT_POD_TYPE), [ broker_task_id_0 ])
    sdk_tasks.check_running(config.SERVICE_NAME, config.DEFAULT_BROKER_COUNT)

    broker_task_id_1 = sdk_tasks.get_task_ids(config.SERVICE_NAME, task_short_name)[0]

    # extract starting and started lines from log
    starting_time = started_time = None
    retry_seconds_remaining = max_restart_seconds + startup_padding_seconds
    while retry_seconds_remaining > 0.0 and (starting_time is None or started_time is None):
        stdout = sdk_cmd.run_cli("task log --lines=1000 {}".format(broker_task_id_1))
        task_lines = stdout.split('\n')
        for log_line in reversed(task_lines):
            if starting_time is None and ' starting (kafka.server.KafkaServer)' in log_line:
                starting_time = log_line_ts(log_line)
            elif started_time is None and ' started (kafka.server.KafkaServer)' in log_line:
                started_time = log_line_ts(log_line)
        if starting_time is None or started_time is None:
            time.sleep(retry_delay_seconds)

    if started_time is None or starting_time is None:
        f = open('/tmp/kafka_startup_stdout', 'w')
        f.write(stdout)
        f.close()

    if starting_time is None:
        starting_time = starting_fallback_time

    assert starting_time is not None
    assert started_time is not None
    assert started_time >= starting_time
    assert (started_time - starting_time).total_seconds() <= max_restart_seconds


log_timestamp_re = re.compile('\[(\d{4}[-]\d{2}[-]\d{2}[ ]\d{2}[:]\d{2}[:]\d{2})')
def log_line_ts(log_line):
    m = log_timestamp_re.match(log_line)
    if m is None:
        return None
    else:
        ts = log_line[m.start() + 1:m.end()]
        # return parse(ts)
        return datetime.datetime(*tuple(map(lambda it: int(it), [ ts[0:4], ts[5:7], ts[8:10], ts[11:13], ts[14:16], ts[17:19] ] )))
