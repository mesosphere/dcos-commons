import sdk_marathon as marathon
import sdk_tasks as tasks


PACKAGE_NAME = 'hello-world'
DEFAULT_TASK_COUNT = 3


def configured_task_count():
    config = marathon.get_config(PACKAGE_NAME)
    return int(config['env']['HELLO_COUNT']) + int(config['env']['WORLD_COUNT'])


def check_running():
    tasks.check_running(PACKAGE_NAME, configured_task_count())
