#!/usr/bin/python

import dcos
import shakedown
import sdk_cmd

# Utilities relating to interaction with service plans


def get_deployment_plan():
    return _get_plan("deploy")


def get_sidecar_plan():
    return _get_plan("sidecar")


def start_sidecar_plan():
    return dcos.http.post(shakedown.dcos_service_url(PACKAGE_NAME) + "/v1/plans/sidecar/start")


def _get_plan(plan):
    def fn():
        try:
            return dcos.http.get("{}/v1/plans/{}".format(shakedown.dcos_service_url(PACKAGE_NAME), plan))
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(response):
        print('Waiting for 200 response')
        success = False

        if hasattr(response, 'status_code'):
            success = response.status_code == 200

        return (
            success,
            'Failed to reach deployment endpoint'
        )

    return spin(fn, success_predicate)
