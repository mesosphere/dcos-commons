'''Utilities relating to interaction with Marathon

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_marathon IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import logging
import json
import os
import retrying
import tempfile

import sdk_cmd
import sdk_tasks

TIMEOUT_SECONDS = 15 * 60

log = logging.getLogger(__name__)


def app_exists(app_name, timeout=TIMEOUT_SECONDS):
    # Custom config fetch: Allow 404 as signal that app doesn't exist. Retry on other errors.
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout * 1000,
        retry_on_exception=lambda e: isinstance(e, Exception))
    def _app_exists():
        response = sdk_cmd.cluster_request('GET', _api_url('apps/{}'.format(app_name)), raise_on_error=False)
        if response.status_code == 404:
            return False  # app doesn't exist
        response.raise_for_status()  # throw exception for (non-404) errors
        return True  # didn't get 404, and no other error code was returned, so app must exist.
    return _app_exists()


def get_config(app_name, timeout=TIMEOUT_SECONDS):
    config = _get_config(app_name)

    # The configuration JSON that marathon returns doesn't match the configuration JSON it accepts,
    # so we have to remove some offending fields to make it re-submittable, since it's not possible to
    # submit a partial config with only the desired fields changed.
    if 'uris' in config:
        del config['uris']

    if 'version' in config:
        del config['version']

    return config


def _is_app_running(app: dict, add_log: str) -> bool:
    staged = app.get('tasksStaged', 0)
    unhealthy = app.get('tasksUnhealthy', 0)
    running = app.get('tasksRunning', 0)
    log.info('{}: staged={}, running={}, unhealthy={}{}'.format(
        app.get('id', '???'), staged, unhealthy, running, add_log))
    return staged == 0 and unhealthy == 0 and running > 0


def _is_app_healthy(app: dict) -> bool:
    healthy = app.get('tasksHealthy', 0)
    return _is_app_running(app, ', healthy={}'.format(healthy)) and healthy > 0


def wait_for_app_running(app_name: str, timeout: int) -> None:
    @retrying.retry(stop_max_delay=timeout * 1000,
                    wait_fixed=2000,
                    retry_on_result=lambda result: not result)
    def _wait_for_app_running(app_name: str) -> bool:
        return _is_app_running(_get_config(app_name), "")

    log.info('Waiting for {} to be running'.format(app_name))
    _wait_for_app_running(app_name)


def wait_for_app_healthy(app_name: str, timeout: int) -> None:
    @retrying.retry(stop_max_delay=timeout * 1000,
                    wait_fixed=2000,
                    retry_on_result=lambda result: not result)
    def _wait_for_app_healthy(app_name: str) -> bool:
        return _is_app_healthy(_get_config(app_name))

    log.info('Waiting for {} to be healthy'.format(app_name))
    _wait_for_app_healthy(app_name)


def wait_for_deployment(app_name: str, timeout: int) -> None:
    @retrying.retry(stop_max_delay=timeout * 1000,
                    wait_fixed=2000,
                    retry_on_result=lambda result: not result)
    def _wait_for_deployment(app_name):
        deployments = sdk_cmd.cluster_request('GET', _api_url('deployments')).json()
        filtered_deployments = [d for d in deployments if app_name in d['affectedApps']]
        log.info('Found {} deployment{} for {}'.format(
            len(filtered_deployments),
            '' if len(filtered_deployments) == 1 else 's',
            app_name))
        return len(filtered_deployments) == 0

    log.info('Waiting for {} to have no pending deployments'.format(app_name))
    _wait_for_deployment(app_name)


def wait_for_deployment_and_app_running(app_name: str, timeout: int) -> None:
    wait_for_deployment(app_name, timeout)
    wait_for_app_running(app_name, timeout)


def install_app_from_file(app_name: str, app_def_path: str) -> (bool, str):
    """
    Installs a marathon app using the path to an app definition.

    Args:
        app_def_path: Path to app definition

    Returns:
        (bool, str) tuple: Boolean indicates success of install attempt. String indicates
        error message if install attempt failed.
    """

    log.info("Launching app {} with definition in {}".format(app_name, app_def_path))
    rc, stdout, stderr = sdk_cmd.run_raw_cli("marathon app add {}".format(app_def_path))

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

    with tempfile.NamedTemporaryFile('w') as app_file:
        json.dump(app_definition, app_file)
        app_file.flush()  # ensure content is available for the CLI to read below

        return install_app_from_file(app_name, app_file.name)


def update_app(app_name, config, timeout=TIMEOUT_SECONDS, wait_for_completed_deployment=True, force=True):
    if "env" in config:
        log.info("Environment for marathon app {} ({} values):".format(app_name, len(config["env"])))
        for k in sorted(config["env"]):
            log.info("  {}={}".format(k, config["env"][k]))

    force_params = {'force': 'true'} if force else {}

    # throws on failure:
    sdk_cmd.cluster_request('PUT', _api_url('apps/{}'.format(app_name)), log_args=False, params=force_params, json=config)

    if wait_for_completed_deployment:
        log.info("Waiting for Marathon deployment of {} to complete...".format(app_name))
        wait_for_deployment(app_name, timeout)


def destroy_app(app_name, timeout=TIMEOUT_SECONDS):
    sdk_cmd.cluster_request('DELETE', _api_url('apps/{}'.format(app_name)), params={'force': 'true'})
    wait_for_deployment(app_name, timeout)


def restart_app(app_name):
    log.info("Restarting {}...".format(app_name))
    # throws on failure:
    sdk_cmd.cluster_request('POST', _api_url('apps/{}/restart'.format(app_name)))
    log.info("Restarted {}.".format(app_name))


def _get_config(app_name):
    return sdk_cmd.cluster_request('GET', _api_url('apps/{}'.format(app_name))).json()['app']


def _api_url(path):
    return '/marathon/v2/{}'.format(path)


def get_scheduler_host(service_name):
    # Marathon mangles foldered paths as follows: "/path/to/svc" => "svc.to.path"
    task_name_elems = service_name.lstrip('/').split('/')
    task_name_elems.reverse()
    app_name = '.'.join(task_name_elems)
    ips = [t.host for t in sdk_tasks.get_service_tasks('marathon', app_name)]
    if len(ips) == 0:
        raise Exception('No IPs found for marathon task "{}". Available tasks are: {}'.format(
            app_name, [task['name'] for task in sdk_tasks.get_service_tasks('marathon')]))
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
