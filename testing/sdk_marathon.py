'''Utilities relating to interaction with Marathon

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_marathon IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import logging
import json

import shakedown

import sdk_cmd

log = logging.getLogger(__name__)


def get_config(app_name):
    # Be permissive of flakes when fetching the app content:
    def fn():
        return sdk_cmd.request('get', api_url('apps/{}'.format(app_name)), retry=False, log_args=False)
    config = shakedown.wait_for(lambda: fn()).json()['app']

    # The configuration JSON that marathon returns doesn't match the configuration JSON it accepts,
    # so we have to remove some offending fields to make it re-submittable, since it's not possible to
    # submit a partial config with only the desired fields changed.
    if 'uris' in config:
        del config['uris']

    if 'version' in config:
        del config['version']

    return config


def install_app_from_file(app_name: str, app_def_path: str) -> (bool, str):
    """
    Installs a marathon app using the path to an app definition.

    Args:
        app_def_path: Path to app definition

    Returns:
        (bool, str) tuple: Boolean indicates success of install attempt. String indicates
        error message if install attempt failed.
    """
    output = sdk_cmd.run_cli("{cmd} {file_path}".format(
        cmd="marathon app add ", file_path=app_def_path
    ))
    if "Created deployment" not in output:
        return 1, output

    log.info("Waiting for app to be running...")
    shakedown.wait_for_task("marathon", app_name)
    return 0, ""



def install_app(app_definition: dict) -> (bool, str):
    """
    Installs a marathon app using the given `app_definition`.

    Args:
        app_definition: The definition of the app to pass to marathon.

    Returns:
        (bool, str) tuple: Boolean indicates success of install attempt. String indicates
        error message if install attempt failed.
    """
    app_name = app_definition["id"]
    app_def_file = "{}.json".format(app_name)

    log.info("Launching {} marathon app".format(app_name))

    with open(app_def_file, "w") as f:
        json.dump(app_definition, f)

    return install_app_from_file(app_name, app_def_file)


def update_app(app_name, config, timeout=600, wait_for_completed_deployment=True):
    if "env" in config:
        log.info("Environment for marathon app {} ({} values):".format(app_name, len(config["env"])))
        for k in sorted(config["env"]):
            log.info("  {}={}".format(k, config["env"][k]))
    response = sdk_cmd.request('put', api_url('apps/{}'.format(app_name)), log_args=False, json=config)

    assert response.ok, "Marathon configuration update failed for {} with config {}".format(app_name, config)

    if wait_for_completed_deployment:
        log.info("Waiting for Marathon deployment of {} to complete...".format(app_name))
        shakedown.deployment_wait(app_id=app_name, timeout=timeout)


def destroy_app(app_name):
    shakedown.delete_app_wait(app_name)


def restart_app(app_name):
    log.info("Restarting {}...".format(app_name))
    response = sdk_cmd.request('post', api_url('apps/{}/restart'.format(app_name)))
    log.info(response)
    assert response.ok
    log.info("Restarted {}.".format(app_name))


def api_url(basename):
    return '{}/v2/{}'.format(shakedown.dcos_service_url('marathon'), basename)


def api_url_with_param(basename, path_param):
    return '{}/{}'.format(api_url(basename), path_param)


def get_scheduler_host(package_name):
    # Marathon mangles foldered paths as follows: "/path/to/svc" => "svc.to.path"
    task_name_elems = package_name.lstrip('/').split('/')
    task_name_elems.reverse()
    app_name = '.'.join(task_name_elems)
    ips = shakedown.get_service_ips('marathon', app_name)
    if len(ips) == 0:
        raise Exception('No IPs found for marathon task "{}". Available tasks are: {}'.format(
            app_name, [task['name'] for task in shakedown.get_service_tasks('marathon')]))
    return ips.pop()


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
