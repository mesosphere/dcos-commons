'''Utilities relating to interaction with service plans'''

import dcos
import sdk_api
import sdk_spin
import sdk_utils
import shakedown


def get_deployment_plan(service_name):
    return get_plan(service_name, "deploy")


def get_sidecar_plan(service_name):
    return get_plan(service_name, "sidecar")


def start_sidecar_plan(service_name, parameters=None):
    start_plan(service_name, "sidecar", parameters)


def start_plan(service_name, plan, parameters=None):
    return dcos.http.post("{}/v1/plans/{}/start".format(shakedown.dcos_service_url(service_name), plan),
                          json=parameters if parameters is not None else {})


def get_plan(service_name, plan):
    sdk_utils.out("Waiting for {} plan to complete...".format(service_name))

    def fn():
        return sdk_api.get(service_name, "/v1/plans/{}".format(plan))
    return sdk_spin.time_wait_return(fn)


def wait_for_completed_deployment(service_name):
    def fn():
        return deployment_plan_is_finished(service_name)
    return sdk_spin.time_wait_return(fn)


def wait_for_completed_recovery(service_name):
    def fn():
        return recovery_plan_is_finished(service_name)
    return sdk_spin.time_wait_return(fn)


def deployment_plan_is_finished(service_name):
    finished = plan_is_finished(service_name, 'deploy')
    sdk_utils.out("Deployment plan for {} is finished: {}".format(service_name, finished))
    return finished


def recovery_plan_is_finished(service_name):
    finished = plan_is_finished(service_name, 'recovery')
    sdk_utils.out("Recovery plan for {} is finished: {}".format(service_name, finished))
    return finished


def plan_is_finished(service_name, plan):
    plan = get_plan(service_name, plan).json()
    return plan['status'] == 'COMPLETE'
