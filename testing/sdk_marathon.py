"""Utilities relating to interaction with Marathon

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_marathon IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import logging
import retrying
import requests

import sdk_cmd
import sdk_tasks

TIMEOUT_SECONDS = 15 * 60

log = logging.getLogger(__name__)


def app_exists(app_name, timeout=TIMEOUT_SECONDS):
    # Custom config fetch: Allow 404 as signal that app doesn't exist. Retry on other errors.
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout * 1000,
        retry_on_exception=lambda e: isinstance(e, Exception),
    )
    def _app_exists():
        response = sdk_cmd.cluster_request(
            "GET", _api_url("apps/{}".format(app_name)), raise_on_error=False
        )
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
    if "uris" in config:
        del config["uris"]

    if "version" in config:
        del config["version"]

    return config


class MarathonDeploymentResponse:
    def __init__(self, response: requests.Response) -> None:
        try:
            response_json = response.json()
        except ValueError:
            log.error("Failed to parse marathon response as JSON: %s", response.text)
            raise
        log.info("Marathon deployment response JSON: %s", response_json)
        response.raise_for_status()

        self._version = response_json["version"]
        if response.status_code == 201:
            self._deployment_id = self._parse_deployment_id_on_app_creation(response_json)
        else:
            self._deployment_id = response_json["deploymentId"]

    def _parse_deployment_id_on_app_creation(self, response_json: dict) -> str:
        return response_json["deployments"][0]["id"]

    def get_deployment_id(self) -> str:
        return self._deployment_id

    def get_version(self) -> str:
        return self._version


def wait_for_deployment(app_name: str, timeout: int, expected_version: str) -> None:
    @retrying.retry(
        stop_max_delay=timeout * 1000, wait_fixed=2000, retry_on_result=lambda result: not result
    )
    def _wait_for_deployment() -> bool:
        app = _get_config(app_name)

        if expected_version:
            # Specific version expected: Check version in addition to other checks
            # This avoids a race when reconfiguring a marathon app, where it may initially look
            # healthy/deployed BEFORE the deployment has started.
            version = app.get("version", "")
            log_extra = ", version={}/{}".format(version, expected_version)
            extra_check = expected_version == version
        else:
            # No expected version: Just check deployments + health
            # This should ONLY be used when installing a new app, NOT when reconfiguring an existing app.
            log_extra = ""
            extra_check = True

        running = app.get("tasksRunning", 0)
        healthy = app.get("tasksHealthy", 0)
        expect_instances = app.get("instances", 1)

        if app.get("healthChecks", []) or app.get("readinessChecks", []):
            # App defines health or readiness check.
            # Use the healthy count to determine when the app has finished.
            log_running = running
            log_healthy = "{}/{}+".format(healthy, expect_instances)
            instances_check = healthy >= expect_instances
        else:
            # No health checks, just check 'running'
            log_running = "{}/{}+".format(running, expect_instances)
            log_healthy = healthy
            instances_check = running >= expect_instances

        staged = app.get("tasksStaged", 0)
        unhealthy = app.get("tasksUnhealthy", 0)
        deployments = app.get("deployments", [])

        log.info(
            "%s: staged=%s/0, running=%s, unhealthy=%s/0, healthy=%s, deployments=%s/0%s",
            app.get("id", "???"),
            staged,
            log_running,
            unhealthy,
            log_healthy,
            len(deployments),
            log_extra,
        )
        return (
            staged == 0
            and unhealthy == 0
            and len(deployments) == 0
            and instances_check
            and extra_check
        )

    if expected_version:
        log.info(
            "Waiting for {} to be deployed with version {}...".format(app_name, expected_version)
        )
    else:
        log.info("Waiting for {} to be deployed with any version...".format(app_name))
    _wait_for_deployment()


def install_app(app_definition: dict, timeout=TIMEOUT_SECONDS) -> None:
    """
    Installs a marathon app using the given `app_definition`.

    Args:
        app_definition: The definition of the app to pass to marathon.

    Returns:
        (bool, str) tuple: Boolean indicates success of install attempt. String indicates
        error message if install attempt failed.
    """
    app_name = app_definition["id"]
    log.info("Installing app: {}".format(app_name))

    @retrying.retry(stop_max_delay=timeout * 1000, wait_fixed=2000)
    def _install() -> MarathonDeploymentResponse:
        response = sdk_cmd.cluster_request(
            "POST", _api_url("apps"), json=app_definition, log_args=False, raise_on_error=False
        )
        try:
            deployment_response = MarathonDeploymentResponse(response)
        except requests.HTTPError as e:
            if e.response.status_code == 409:
                # App exists already, left over from previous run? Delete and try again.
                destroy_app(app_name, timeout=timeout)
            raise e
        return deployment_response

    result = _install()
    wait_for_deployment(app_name, timeout, result.get_version())


def update_app(
    config: dict, timeout=TIMEOUT_SECONDS, wait_for_completed_deployment=True, force=True
) -> None:
    app_name = config["id"]
    if "env" in config:
        log.info(
            "Environment for marathon app {} ({} values):".format(app_name, len(config["env"]))
        )
        for k in sorted(config["env"]):
            log.info("  {}={}".format(k, config["env"][k]))

    @retrying.retry(stop_max_delay=timeout * 1000, wait_fixed=2000)
    def _update() -> MarathonDeploymentResponse:
        response = sdk_cmd.cluster_request(
            "PUT",
            _api_url("apps/{}".format(app_name)),
            params={"force": "true"} if force else {},
            json=config,
            log_args=False,
            raise_on_error=False,
        )
        return MarathonDeploymentResponse(response)

    result = _update()

    # Sometimes the caller expects the update to fail.
    # Allow those cases to skip waiting for successful deployment:
    if wait_for_completed_deployment:
        wait_for_deployment(app_name, timeout, result.get_version())


def destroy_app(app_name: str, timeout=TIMEOUT_SECONDS) -> None:
    @retrying.retry(stop_max_delay=timeout * 1000, wait_fixed=2000)
    def _destroy() -> MarathonDeploymentResponse:
        response = sdk_cmd.cluster_request(
            "DELETE", _api_url("apps/{}".format(app_name)), params={"force": "true"}
        )
        return MarathonDeploymentResponse(response)

    result = _destroy()
    deployment_id = result.get_deployment_id()

    # This check is different from the other deployment checks.
    # When it's complete, the app is gone entirely.
    @retrying.retry(
        stop_max_delay=timeout * 1000, wait_fixed=2000, retry_on_result=lambda result: not result
    )
    def _wait_for_app_destroyed():
        if app_exists(app_name, timeout):
            return False
        running_deployments = sdk_cmd.cluster_request("GET", _api_url("deployments")).json()
        log.info(
            "While waiting to delete %s, currently running marathon deployments: %s",
            deployment_id,
            running_deployments,
        )
        return deployment_id not in [d["id"] for d in running_deployments]

    log.info("Waiting for {} to be removed...".format(app_name))
    _wait_for_app_destroyed()


def restart_app(app_name: str, timeout=TIMEOUT_SECONDS) -> None:
    @retrying.retry(stop_max_delay=timeout * 1000, wait_fixed=2000)
    def _restart() -> MarathonDeploymentResponse:
        response = sdk_cmd.cluster_request(
            "POST", _api_url("apps/{}/restart".format(app_name)), raise_on_error=False
        )
        return MarathonDeploymentResponse(response)

    result = _restart()

    wait_for_deployment(app_name, timeout, result.get_version())


def _get_config(app_name):
    return sdk_cmd.cluster_request("GET", _api_url("apps/{}".format(app_name))).json()["app"]


def _api_url(path):
    return "/marathon/v2/{}".format(path)


def get_scheduler_task_prefix(service_name):
    '''Marathon mangles foldered paths as follows: "/path/to/svc" => "svc.to.path"'''
    task_name_elems = service_name.lstrip("/").split("/")
    task_name_elems.reverse()
    return ".".join(task_name_elems)


def get_scheduler_host(service_name):
    task_prefix = get_scheduler_task_prefix(service_name)
    tasks = sdk_tasks.get_service_tasks("marathon", task_prefix=task_prefix)
    if len(tasks) == 0:
        raise Exception(
            "No marathon tasks starting with '{}' were found. Available tasks are: {}".format(
                task_prefix, [task["name"] for task in sdk_tasks.get_service_tasks("marathon")]
            )
        )
    return tasks.pop().host


def bump_cpu_count_config(service_name, key_name, delta=0.1):
    config = get_config(service_name)
    updated_cpus = float(config["env"][key_name]) + delta
    config["env"][key_name] = str(updated_cpus)
    update_app(config)
    return updated_cpus


def bump_task_count_config(service_name, key_name, delta=1):
    config = get_config(service_name)
    updated_node_count = int(config["env"][key_name]) + delta
    config["env"][key_name] = str(updated_node_count)
    update_app(config)
    return updated_node_count
