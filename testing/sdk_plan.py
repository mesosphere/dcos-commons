'''Utilities relating to interaction with service plans

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_plan IN ANY OTHER PARTNER REPOS
************************************************************************
'''

import json
import logging
import os.path
import traceback

import retrying

import sdk_cmd
import sdk_utils

TIMEOUT_SECONDS = 15 * 60
SHORT_TIMEOUT_SECONDS = 30

log = logging.getLogger(__name__)


def get_deployment_plan(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return get_plan(service_name, 'deploy', timeout_seconds)


def get_recovery_plan(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return get_plan(service_name, 'recovery', timeout_seconds)


def list_plans(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return sdk_cmd.service_request('GET', service_name, '/v1/plans').json()


def get_plan(service_name, plan, timeout_seconds=TIMEOUT_SECONDS):
    # We need to DIY error handling/retry because the query will return 417 if the plan has errors.
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000)
    def wait_for_plan():
        response = sdk_cmd.service_request(
            'GET', service_name, '/v1/plans/{}'.format(plan),
            raise_on_error=False)
        if response.status_code == 417:
            return response # avoid throwing, return plan with errors
        response.raise_for_status()
        return response

    return wait_for_plan().json()


def start_plan(service_name, plan, parameters=None):
    sdk_cmd.service_request(
        'POST', service_name, '/v1/plans/{}/start'.format(plan),
        json=parameters if parameters is not None else {})


def wait_for_completed_recovery(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_completed_plan(service_name, 'recovery', timeout_seconds)


def wait_for_in_progress_recovery(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_in_progress_plan(service_name, 'recovery', timeout_seconds)


def wait_for_kicked_off_deployment(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_kicked_off_plan(service_name, 'deploy', timeout_seconds)


def wait_for_kicked_off_recovery(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_kicked_off_plan(service_name, 'recovery', timeout_seconds)


def wait_for_completed_deployment(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_completed_plan(service_name, 'deploy', timeout_seconds)


def wait_for_completed_plan(service_name, plan_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_plan_status(service_name, plan_name, 'COMPLETE', timeout_seconds)


def wait_for_completed_phase(service_name, plan_name, phase_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_phase_status(service_name, plan_name, phase_name, 'COMPLETE', timeout_seconds)


def wait_for_completed_step(service_name, plan_name, phase_name, step_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_step_status(service_name, plan_name, phase_name, step_name, 'COMPLETE', timeout_seconds)


def wait_for_kicked_off_plan(service_name, plan_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_plan_status(service_name, plan_name, ['STARTING', 'IN_PROGRESS'], timeout_seconds)


def wait_for_in_progress_plan(service_name, plan_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_plan_status(service_name, plan_name, 'IN_PROGRESS', timeout_seconds)


def wait_for_starting_plan(service_name, plan_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_plan_status(service_name, plan_name, 'STARTING', timeout_seconds)


def wait_for_plan_status(service_name, plan_name, status, timeout_seconds=TIMEOUT_SECONDS):
    '''Wait for a plan to have one of the specified statuses'''
    if isinstance(status, str):
        statuses = [status, ]
    else:
        statuses = status

    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: not res)
    def fn():
        plan = get_plan(service_name, plan_name, SHORT_TIMEOUT_SECONDS)
        log.info('Waiting for {} plan to have {} status:\n{}'.format(
            plan_name, status, plan_string(plan_name, plan)))
        if plan and plan['status'] in statuses:
            return plan
        else:
            return False

    return fn()


def wait_for_phase_status(service_name, plan_name, phase_name, status, timeout_seconds=TIMEOUT_SECONDS):
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: not res)
    def fn():
        plan = get_plan(service_name, plan_name, SHORT_TIMEOUT_SECONDS)
        phase = get_phase(plan, phase_name)
        log.info('Waiting for {}.{} phase to have {} status:\n{}'.format(
            plan_name, phase_name, status, plan_string(plan_name, plan)))
        if phase and phase['status'] == status:
            return plan
        else:
            return False

    return fn()


def wait_for_step_status(service_name, plan_name, phase_name, step_name, status, timeout_seconds=TIMEOUT_SECONDS):
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: not res)
    def fn():
        plan = get_plan(service_name, plan_name, SHORT_TIMEOUT_SECONDS)
        step = get_step(get_phase(plan, phase_name), step_name)
        log.info('Waiting for {}.{}.{} step to have {} status:\n{}'.format(
            plan_name, phase_name, step_name, status, plan_string(plan_name, plan)))
        if step and step['status'] == status:
            return plan
        else:
            return False

    return fn()


def recovery_plan_is_empty(service_name):
    plan = get_recovery_plan(service_name)
    return len(plan['phases']) == 0 and len(plan['errors']) == 0 and plan['status'] == 'COMPLETE'


def get_phase(plan, name):
    return get_child(plan, 'phases', name)


def get_step(phase, name):
    return get_child(phase, 'steps', name)


def get_child(parent, children_field, name):
    if parent is None:
        return None
    for child in parent[children_field]:
        if child['name'] == name:
            return child
    return None


def plan_string(plan_name, plan):
    if plan is None:
        return '{}=NULL!'.format(plan_name)

    def phase_string(phase):
        ''' Formats the phase output as follows:

        deploy STARTING:
        - node-deploy STARTING: node-0:[server]=STARTING, node-1:[server]=PENDING, node-2:[server]=PENDING
        - node-other PENDING: somestep=PENDING
        - errors: foo, bar
        '''
        return '\n- {} {}: {}'.format(
            phase['name'],
            phase['status'],
            ', '.join('{}={}'.format(step['name'], step['status']) for step in phase['steps']))

    plan_str = '{} {}:{}'.format(
        plan_name,
        plan['status'],
        ''.join(phase_string(phase) for phase in plan['phases']))
    if plan.get('errors', []):
        plan_str += '\n- errors: {}'.format(', '.join(plan['errors']))
    return plan_str


def log_plans_if_failed(framework_name, request):
    """If the test had failed, writes the plan state to a log file.

    This should generally be used as a fixture in a framework's conftest.py:

    @pytest.fixture(autouse=True)
    def get_plans_on_failure(request):
        yield from sdk_plan.log_plans_if_failed(framework_name, request)
    """
    yield
    if sdk_utils.is_test_failure(request):
        try:
            log.info('Fetching plans from {}...'.format(framework_name))
            plan_names = list_plans(framework_name, 5)
            log.info('Plans for {}: {}'.format(framework_name, plan_names))
            for plan_name in plan_names:
                log.info('Fetching {} plan: {}'.format(framework_name, plan_name))
                plan = get_plan(framework_name, plan_name, 5)
                if not plan:
                    log.error('Unable to fetch {} plan for {}'.format(framework_name, plan_name))
                    continue
                out_path = os.path.join(
                    sdk_utils.get_test_log_directory(request.node),
                    '{}_plan.txt'.format(plan_name))
                out_content = json.dumps(plan, indent=2)
                log.info('=> Writing {} ({} bytes)'.format(out_path, len(out_content)))
                with open(out_path, 'w') as f:
                    f.write(out_content)
        except:
            log.error('Exception when getting plan dump following a failed test: {}'.format(traceback.format_exc()))
