'''Utilities relating to interaction with Marathon'''

import sdk_cmd
import sdk_spin
import shakedown


def get_config(app_name):
    def fn():
        return sdk_cmd.request('get', api_url('apps/{}'.format(app_name)), retry=False)

    config = sdk_spin.time_wait_return(lambda: fn()).json()['app']
    del config['uris']
    del config['version']

    return config


def update_app(app_name, config):
    response = sdk_cmd.request('put', api_url('apps/{}'.format(app_name)), json=config)
    assert response.ok, "Marathon configuration update failed for {} with config {}".format(app_name, config)


def destroy_app(app_name):
    sdk_cmd.request('delete', api_url_with_param('apps', app_name))

    # Make sure the scheduler has been destroyed
    sdk_spin.time_wait_noisy(lambda: (shakedown.get_service(app_name) is None))


def api_url(basename):
    return '{}/v2/{}'.format(shakedown.dcos_service_url('marathon'), basename)


def api_url_with_param(basename, path_param):
    return '{}/{}'.format(api_url(basename), path_param)
