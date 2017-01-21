#!/usr/bin/python

import dcos
import shakedown

# Utilities relating to interaction with Marathon


def get_marathon_config():
    response = dcos.http.get(marathon_api_url('apps/{}/versions'.format(PACKAGE_NAME)))
    assert response.status_code == 200, 'Marathon versions request failed'

    last_index = len(response.json()['versions']) - 1
    version = response.json()['versions'][last_index]

    response = dcos.http.get(marathon_api_url('apps/{}/versions/{}'.format(PACKAGE_NAME, version)))
    assert response.status_code == 200

    config = response.json()
    del config['uris']
    del config['version']

    return config


def destroy_marathon_app(app_name):
    request(dcos.http.delete, marathon_api_url_with_param('apps', app_name))
    # Make sure the scheduler has been destroyed

    def fn():
        shakedown.get_service(app_name)

    def success_predicate(service):
        return service is None, 'Service not destroyed'

    spin(fn, success_predicate)


def marathon_api_url(basename):
    return '{}/v2/{}'.format(shakedown.dcos_service_url('marathon'), basename)


def marathon_api_url_with_param(basename, path_param):
    return '{}/{}'.format(marathon_api_url(basename), path_param)
