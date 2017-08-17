'''Utilities relating to interaction with service plans'''
import logging

import dcos
import sdk_api
import shakedown

TIMEOUT_SECONDS = 15 * 60

log = logging.getLogger(__name__)


def get_deployment_plan(service_name):
    return get_plan(service_name, "deploy")


def get_plan(service_name, plan):
    def fn():
        output = sdk_api.get(service_name, '/v1/plans/{}'.format(plan))
        try:
            return output.json()
        except:
            return False
    return shakedown.wait_for(fn)


def start_plan(service_name, plan, parameters=None):
    return dcos.http.post(
        "{}/v1/plans/{}/start".format(shakedown.dcos_service_url(service_name), plan),
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

    def fn():
        plan = get_plan(service_name, plan_name)
        log.info('Waiting for {} plan to have {} status:\nFound:\n{}'.format(
            plan_name, status, plan_string(plan_name, plan)))
        if plan and plan['status'] in statuses:
            return plan
        else:
            return False
    return shakedown.wait_for(fn, noisy=True, timeout_seconds=timeout_seconds)


def wait_for_phase_status(service_name, plan_name, phase_name, status, timeout_seconds=TIMEOUT_SECONDS):
    def fn():
        plan = get_plan(service_name, plan_name)
        phase = get_phase(plan, phase_name)
        log.info('Waiting for {}.{} phase to have {} status:\n{}'.format(
            plan_name, phase_name, status, plan_string(plan_name, plan)))
        if phase and phase['status'] == status:
            return plan
        else:
            return False
    return shakedown.wait_for(fn, noisy=True, timeout_seconds=timeout_seconds)


def wait_for_step_status(service_name, plan_name, phase_name, step_name, status, timeout_seconds=TIMEOUT_SECONDS):
    def fn():
        plan = get_plan(service_name, plan_name)
        step = get_step(get_phase(plan, phase_name), step_name)
        log.info('Waiting for {}.{}.{} step to have {} status:\n{}'.format(
            plan_name, phase_name, step_name, status, plan_string(plan_name, plan)))
        if step and step['status'] == status:
            return plan
        else:
            return False
    return shakedown.wait_for(fn, noisy=True, timeout_seconds=timeout_seconds)


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
