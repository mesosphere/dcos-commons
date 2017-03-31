import shakedown

import sdk_marathon as marathon
import sdk_tasks as tasks

PACKAGE_NAME = 'hello-world'
DEFAULT_TASK_COUNT = 3


def configured_task_count():
    return hello_task_count() + world_task_count()


def hello_task_count():
    config = marathon.get_config(PACKAGE_NAME)
    return int(config['env']['HELLO_COUNT'])


def world_task_count():
    config = marathon.get_config(PACKAGE_NAME)
    return int(config['env']['WORLD_COUNT'])


def check_running():
    tasks.check_running(PACKAGE_NAME, configured_task_count())


def get_node_host():
    return shakedown.get_service_ips(PACKAGE_NAME).pop()


def bump_cpu_count_config():
    config = marathon.get_config(PACKAGE_NAME)
    config['env']['HELLO_CPUS'] = str(
        float(config['env']['HELLO_CPUS']) + 0.1
    )
    marathon.update_app(PACKAGE_NAME, config)


# TODO replace this?
def run_planned_operation(operation, failure=lambda: None):
    plan = get_and_verify_plan()

    operation()
    pred = lambda p: (
        plan['phases'][1]['id'] != p['phases'][1]['id'] or
        len(plan['phases']) < len(p['phases']) or
        p['status'] == 'InProgress'
    )
    next_plan = get_and_verify_plan(
        lambda p: (
            plan['phases'][1]['id'] != p['phases'][1]['id'] or
            len(plan['phases']) < len(p['phases']) or
            p['status'] == 'InProgress'
        )
    )

    failure()
    completed_plan = get_and_verify_plan(lambda p: p['status'] == 'Complete')
