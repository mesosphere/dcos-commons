'''Utilities relating to interaction with Marathon'''

import sdk_cmd

from dcos import marathon
import shakedown


def get_config(app_name):
    def fn():
        return sdk_cmd.request('get', api_url('apps/{}'.format(app_name)), retry=False)

    config = shakedown.wait_for(lambda: fn()).json()['app']
    del config['uris']
    del config['version']

    return config


def destroy_app(app_name):
    shakedown.delete_app_wait(app_name)


def api_url(basename):
    return '{}/v2/{}'.format(shakedown.dcos_service_url('marathon'), basename)


def api_url_with_param(basename, path_param):
    return '{}/{}'.format(api_url(basename), path_param)
