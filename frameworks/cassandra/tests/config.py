import shakedown

PACKAGE_NAME = 'cassandra'
DEFAULT_TASK_COUNT = 3
WAIT_TIME_IN_SECONDS = 360
DCOS_URL = shakedown.run_dcos_command('config show core.dcos_url')[0].strip()
DCOS_TOKEN = shakedown.run_dcos_command('config show core.dcos_acs_token')[0].strip()

TASK_RUNNING_STATE = 'TASK_RUNNING'

REQUEST_HEADERS = {
    'authorization': 'token=%s' % DCOS_TOKEN
}


def check_dcos_service_health():
    return shakedown.service_healthy(PACKAGE_NAME)
