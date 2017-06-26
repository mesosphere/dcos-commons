PACKAGE_NAME = 'cockroachdb'
SERVICE_NAME = 'cockroachdb'
DEFAULT_TASK_COUNT = 3
DEFAULT_POD_TYPE = 'cockroachdb'
DEFAULT_TASK_NAME = 'node'
def check_running(service_name=PACKAGE_NAME):
    tasks.check_running(service_name, configured_task_count(service_name))
