import time

import dcos
import shakedown

from tests.defaults import (
    IS_STRICT,
    PACKAGE_NAME,
    PRINCIPAL,
    TASK_RUNNING_STATE,
)


WAIT_TIME_IN_SECONDS = 15 * 60

DEFAULT_HDFS_TASK_COUNT = 8 # 3 data nodes, 3 journal nodes, 2 name nodes


def check_health():
    def fn():
        try:
            return shakedown.get_service_tasks(PACKAGE_NAME)
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(tasks):
        running_tasks = [t for t in tasks if t['state'] == TASK_RUNNING_STATE]
        print('Waiting for {} healthy tasks, got {}/{}'.format(
            DEFAULT_HDFS_TASK_COUNT, len(running_tasks), len(tasks)))
        return (
            len(running_tasks) >= DEFAULT_HDFS_TASK_COUNT,
            'Service did not become healthy'
        )

    return spin(fn, success_predicate)


def uninstall():
    print('Uninstalling/janitoring {}'.format(PACKAGE_NAME))
    try:
        shakedown.uninstall_package_and_wait(PACKAGE_NAME, app_id=PACKAGE_NAME)
    except (dcos.errors.DCOSException, ValueError) as e:
        print('Got exception when uninstalling package, continuing with janitor anyway: {}'.format(e))

    shakedown.run_command_on_master(
        'docker run mesosphere/janitor /janitor.py {}'
        '-r hdfs-role -p {} -z dcos-service-hdfs '
        '--auth_token={}'.format(
            '-m https://leader.mesos:5050/master/ ' if IS_STRICT else '',
            PRINCIPAL,
            shakedown.run_dcos_command(
                'config show core.dcos_acs_token'
            )[0].strip()
        )
    )


def spin(fn, success_predicate, *args, **kwargs):
    end_time = time.time() + WAIT_TIME_IN_SECONDS
    while time.time() < end_time:
        try:
            result = fn(*args, **kwargs)
        except Exception as e:
            is_successful, error_message = False, str(e)
        else:
            is_successful, error_message = success_predicate(result)

        if is_successful:
            print('Success state reached, exiting spin. prev_err={}'.format(error_message))
            break
        print('Waiting for success state... err={}'.format(error_message))
        time.sleep(1)

    assert is_successful, error_message

    return result
