import sdk_tasks as tasks


PACKAGE_NAME = 'hdfs'
DEFAULT_TASK_COUNT = 10 # 3 data nodes, 3 journal nodes, 2 name nodes, 2 zkfc nodes


def check_running(count = DEFAULT_TASK_COUNT):
    tasks.check_running(PACKAGE_NAME, count)
