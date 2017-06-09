'''Utilities relating to interaction with service plans'''

import dcos
import sdk_api
import sdk_utils
import shakedown


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
    return dcos.http.post("{}/v1/plans/{}/start".format(shakedown.dcos_service_url(service_name), plan),
                          json=parameters if parameters is not None else {})


def wait_for_completed_recovery(service_name, timeout_seconds=15 * 60):
    return wait_for_completed_plan(service_name, 'recovery', timeout_seconds)


def wait_for_completed_deployment(service_name, timeout_seconds=15 * 60):
    return wait_for_completed_plan(service_name, 'deploy', timeout_seconds)


def wait_for_completed_plan(service_name, plan_name, timeout_seconds=15 * 60):
    return wait_for_plan_status(service_name, plan_name, 'COMPLETE', timeout_seconds)


def wait_for_completed_phase(service_name, plan_name, phase_name, timeout_seconds=15 * 60):
    return wait_for_phase_status(service_name, plan_name, phase_name, 'COMPLETE', timeout_seconds)


def wait_for_completed_step(service_name, plan_name, phase_name, step_name, timeout_seconds=15 * 60):
    return wait_for_step_status(service_name, plan_name, phase_name, step_name, 'COMPLETE', timeout_seconds)


def wait_for_plan_status(service_name, plan_name, status, timeout_seconds=15 * 60):
    def fn():
        plan = get_plan(service_name, plan_name)
        if plan['status'] == status:
            return plan
        else:
            return False
    return shakedown.wait_for(fn, noisy=True, timeout_seconds=timeout_seconds)


def wait_for_phase_status(service_name, plan_name, phase_name, status, timeout_seconds=15 * 60):
    def fn():
        plan = get_plan(service_name, plan_name)
        phase = get_phase(plan, phase_name)
        if phase is not None and phase['status'] == status:
            return plan
        else:
            return False
    return shakedown.wait_for(fn, noisy=True, timeout_seconds=timeout_seconds)


def wait_for_step_status(service_name, plan_name, phase_name, step_name, status, timeout_seconds=15 * 60):
    def fn():
        plan = get_plan(service_name, plan_name)
        step = get_step(get_phase(plan, phase_name), step_name)
        if step is not None and step['status'] == status:
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
