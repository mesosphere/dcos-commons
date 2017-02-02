'''Utilities relating to interaction with service plans'''

import dcos
import sdk_cmd
import sdk_spin
import shakedown


def get_deployment_plan(service_name):
    return _get_plan(service_name, "deploy")


def get_sidecar_plan(service_name):
    return _get_plan(service_name, "sidecar")


def start_sidecar_plan(service_name, parameters=None):
    return dcos.http.post(
        shakedown.dcos_service_url(service_name) + "/v1/plans/sidecar/start",
        json=parameters)


def _get_plan(service_name, plan):
    def fn():
        response = dcos.http.get("{}/v1/plans/{}".format(
            shakedown.dcos_service_url(service_name), plan))
        response.raise_for_status()
        return response
    return sdk_spin.time_wait_return(lambda: fn())
