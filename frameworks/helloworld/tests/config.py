import sdk_marathon as marathon
import sdk_tasks as tasks

PACKAGE_NAME = 'hello-world'
DEFAULT_TASK_COUNT = 3


def configured_task_count():
    return hello_task_count() + world_task_count()


def hello_task_count():
    return task_count('HELLO_COUNT')


def world_task_count():
    return task_count('WORLD_COUNT')


def task_count(key_name):
    config = marathon.get_config(PACKAGE_NAME)
    return int(config['env'][key_name])


def check_running():
    tasks.check_running(PACKAGE_NAME, configured_task_count())


def bump_hello_cpus():
    return marathon.bump_cpu_count_config(PACKAGE_NAME, 'HELLO_CPUS')


def bump_world_cpus():
    return marathon.bump_cpu_count_config(PACKAGE_NAME, 'WORLD_CPUS')
