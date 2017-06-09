import sdk_marathon as marathon
import sdk_tasks as tasks

PACKAGE_NAME = 'hello-world'
DEFAULT_TASK_COUNT = 3


def configured_task_count(service_name=PACKAGE_NAME):
    return hello_task_count(service_name) + world_task_count(service_name)


def hello_task_count(service_name=PACKAGE_NAME):
    return task_count('HELLO_COUNT', service_name)


def world_task_count(service_name=PACKAGE_NAME):
    return task_count('WORLD_COUNT', service_name)


def task_count(key_name, service_name=PACKAGE_NAME):
    return int(marathon.get_config(service_name)['env'][key_name])


def check_running(service_name=PACKAGE_NAME):
    tasks.check_running(service_name, configured_task_count(service_name))


def bump_hello_cpus(service_name=PACKAGE_NAME):
    return marathon.bump_cpu_count_config(service_name, 'HELLO_CPUS')


def bump_world_cpus(service_name=PACKAGE_NAME):
    return marathon.bump_cpu_count_config(service_name, 'WORLD_CPUS')
