import sdk_marathon
import sdk_tasks

PACKAGE_NAME = "hello-world"
SERVICE_NAME = PACKAGE_NAME
DEFAULT_TASK_COUNT = 3


def configured_task_count(service_name: str = SERVICE_NAME) -> int:
    return hello_task_count(service_name) + world_task_count(service_name)


def hello_task_count(service_name: str = SERVICE_NAME) -> int:
    return task_count("HELLO_COUNT", service_name)


def world_task_count(service_name: str = SERVICE_NAME) -> int:
    return task_count("WORLD_COUNT", service_name)


def task_count(key_name: str, service_name: str = SERVICE_NAME) -> int:
    return int(sdk_marathon.get_config(service_name)["env"][key_name])


def check_running(service_name: str = SERVICE_NAME) -> None:
    sdk_tasks.check_running(service_name, configured_task_count(service_name))


def bump_hello_cpus(service_name: str = SERVICE_NAME) -> float:
    return sdk_marathon.bump_cpu_count_config(service_name, "HELLO_CPUS")


def bump_world_cpus(service_name: str = SERVICE_NAME) -> float:
    return sdk_marathon.bump_cpu_count_config(service_name, "WORLD_CPUS")


def close_enough(val0, val1):
    epsilon = 0.00001
    diff = abs(val0 - val1)
    return diff < epsilon
