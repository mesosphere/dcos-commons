'''Utilities relating to interaction with Marathon'''

import shakedown

import sdk_cmd
import sdk_spin


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
    shakedown.delete_app_wait(app_name)


def api_url(basename):
    return '{}/v2/{}'.format(shakedown.dcos_service_url('marathon'), basename)


def api_url_with_param(basename, path_param):
    return '{}/{}'.format(api_url(basename), path_param)


def get_scheduler_host(package_name):
    return shakedown.get_service_ips('marathon', package_name).pop()


def bump_cpu_count_config(package_name, key_name, delta=0.1):
    config = get_config(package_name)
    updated_cpus = float(config['env'][key_name]) + delta
    config['env'][key_name] = str(updated_cpus)
    update_app(package_name, config)
    return updated_cpus


def bump_task_count_config(package_name, key_name, delta=1):
    config = get_config(package_name)
    updated_node_count = int(config['env'][key_name]) + delta
    config['env'][key_name] = str(updated_node_count)
    update_app(package_name, config)
