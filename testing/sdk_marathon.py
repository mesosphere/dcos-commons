'''Utilities relating to interaction with Marathon

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_marathon IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import logging
import json
import os
import tempfile

import retrying
import shakedown

import sdk_cmd
import sdk_metrics

TIMEOUT_SECONDS = 15 * 60

log = logging.getLogger(__name__)


def _get_config_once(app_name):
    return sdk_cmd.cluster_request('GET', _api_url('apps/{}'.format(app_name)), retry=False)


def get_app_id(service_name):
    # service_name may already contain a leading slash.
    return '/' + service_name.lstrip('/')


def wait_for_deployment_and_app_removal(app_id, timeout=TIMEOUT_SECONDS):
    """
    Waits for application to be gone, according to Marathon.
    """
    log.info('Waiting for no deployments for {}'.format(app_id))
    shakedown.deployment_wait(timeout, app_id)

    client = shakedown.marathon.create_client()

    def marathon_dropped_app():
        app_ids = [app['id'] for app in client.get_apps()]
        log.info('Marathon app IDs: {}'.format(app_ids))
        matching_app_ids = list(filter(lambda x: x == app_id, app_ids))
        if len(matching_app_ids) > 1:
            log.warning('Found multiple apps with id {}'.format(app_id))
        return len(matching_app_ids) == 0

    log.info('Waiting for no {} Marathon app'.format(app_id))
    shakedown.time_wait(marathon_dropped_app, timeout_seconds=timeout)


@retrying.retry(stop_max_attempt_number=5,
                wait_fixed=5000,
                retry_on_exception=lambda e: isinstance(e, Exception))
def retried_wait_for_deployment_and_app_removal(*args, **kwargs):
    wait_for_deployment_and_app_removal(*args, **kwargs)


def app_exists(app_name):
    try:
        _get_config_once(app_name)
        return True
    except:
        return False


def get_config(app_name, timeout=TIMEOUT_SECONDS):
    # Be permissive of flakes when fetching the app content:
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout*1000)
    def wait_for_response():
        return _get_config_once(app_name).json()['app']

    config = wait_for_response()

    # The configuration JSON that marathon returns doesn't match the configuration JSON it accepts,
    # so we have to remove some offending fields to make it re-submittable, since it's not possible to
    # submit a partial config with only the desired fields changed.
    if 'uris' in config:
        del config['uris']

    if 'version' in config:
        del config['version']

    return config


def is_app_running(app: dict) -> bool:
    return app['tasksStaged'] == 0 and app['tasksUnhealthy'] == 0 and app['tasksRunning'] > 0


def wait_for_deployment_and_app_running(app_name: str, timeout: int):
    shakedown.deployment_wait(timeout, app_name)

    def app_running():
        cmd = 'marathon app show {}'.format(app_name)
        log.info('Running %s', cmd)
        app = sdk_cmd.get_json_output(cmd)
        return is_app_running(app)

    shakedown.time_wait(app_running, timeout_seconds=timeout)


def install_app_from_file(app_name: str, app_def_path: str) -> (bool, str):
    """
    Installs a marathon app using the path to an app definition.

    Args:
        app_def_path: Path to app definition

    Returns:
        (bool, str) tuple: Boolean indicates success of install attempt. String indicates
        error message if install attempt failed.
    """

    cmd = "marathon app add {}".format(app_def_path)
    log.info("Running %s", cmd)
    rc, stdout, stderr = sdk_cmd.run_raw_cli(cmd)

    if rc or stderr:
        log.error("returncode=%s stdout=%s stderr=%s", rc, stdout, stderr)
        return False, stderr

    if "Created deployment" not in stdout:
        stderr = "'Created deployment' not in STDOUT"
        log.error(stderr)
        return False, stderr

    log.info('Waiting for app %s to be deployed and running...', app_name)
    wait_for_deployment_and_app_running(app_name, TIMEOUT_SECONDS)

    return True, ''


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

    with tempfile.TemporaryDirectory() as d:
        app_def_file = "{}.json".format(app_name.replace('/', '__'))
        log.info("Launching {} marathon app".format(app_name))

        app_def_path = os.path.join(d, app_def_file)

        log.info("Writing app definition to %s", app_def_path)
        with open(app_def_path, "w") as f:
            json.dump(app_definition, f)

        return install_app_from_file(app_name, app_def_path)


def update_app(app_name, config, timeout=TIMEOUT_SECONDS, wait_for_completed_deployment=True, force=True):
    if "env" in config:
        log.info("Environment for marathon app {} ({} values):".format(app_name, len(config["env"])))
        for k in sorted(config["env"]):
            log.info("  {}={}".format(k, config["env"][k]))

    query_string = "?force=true" if force else ""

    # throws on failure:
    sdk_cmd.cluster_request('PUT', _api_url('apps/{}{}'.format(app_name, query_string)), log_args=False, json=config)

    if wait_for_completed_deployment:
        log.info("Waiting for Marathon deployment of {} to complete...".format(app_name))
        shakedown.deployment_wait(app_id=app_name, timeout=timeout)


def destroy_app(app_name):
    shakedown.delete_app_wait(app_name)


def restart_app(app_name):
    log.info("Restarting {}...".format(app_name))
    # throws on failure:
    sdk_cmd.cluster_request('POST', _api_url('apps/{}/restart'.format(app_name)))
    log.info("Restarted {}.".format(app_name))


def _api_url(path):
    return '/marathon/v2/{}'.format(path)


def get_scheduler_host(service_name):
    # Marathon mangles foldered paths as follows: "/path/to/svc" => "svc.to.path"
    task_name_elems = service_name.lstrip('/').split('/')
    task_name_elems.reverse()
    app_name = '.'.join(task_name_elems)
    ips = shakedown.get_service_ips('marathon', app_name)
    if len(ips) == 0:
        raise Exception('No IPs found for marathon task "{}". Available tasks are: {}'.format(
            app_name, [task['name'] for task in shakedown.get_service_tasks('marathon')]))
    return ips.pop()


def bump_cpu_count_config(service_name, key_name, delta=0.1):
    config = get_config(service_name)
    updated_cpus = float(config['env'][key_name]) + delta
    config['env'][key_name] = str(updated_cpus)
    update_app(service_name, config)
    return updated_cpus


def bump_task_count_config(service_name, key_name, delta=1):
    config = get_config(service_name)
    updated_node_count = int(config['env'][key_name]) + delta
    config['env'][key_name] = str(updated_node_count)
    update_app(service_name, config)


def get_mesos_api_version(service_name):
    return get_config(service_name)['env']['MESOS_API_VERSION']


def set_mesos_api_version(service_name, api_version, timeout=600):
    '''Sets the mesos API version to the provided value, and then verifies that the scheduler comes back successfully'''
    config = get_config(service_name)
    config['env']['MESOS_API_VERSION'] = api_version
    update_app(service_name, config, timeout=timeout)
    # wait for scheduler to come back and successfully receive/process offers:
    sdk_metrics.wait_for_scheduler_counter_value(service_name, 'offers.processed', 1, timeout_seconds=timeout)
