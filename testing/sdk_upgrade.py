"""
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_upgrade IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import json
import logging
import retrying
import tempfile

import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_repository
import sdk_tasks
import sdk_utils

log = logging.getLogger(__name__)

TIMEOUT_SECONDS = 25 * 60


# Installs a universe version of a package, then upgrades it to a test version
#
# (1) Installs Universe version of framework (after uninstalling any test version).
# (2) Upgrades to test version of framework.
def test_upgrade(
    package_name,
    service_name,
    expected_running_tasks,
    additional_options={},
    test_version_additional_options=None,
    timeout_seconds=TIMEOUT_SECONDS,
    wait_for_deployment=True,
):
    # Allow providing different options dicts to the universe version vs the test version.
    test_version_additional_options = test_version_additional_options or additional_options

    sdk_install.uninstall(package_name, service_name)

    universe_version = None
    try:
        # Move the Universe repo to the top of the repo list so that we can first install the
        # release version.
        test_version, universe_version = sdk_repository.move_universe_repo(
            package_name, universe_repo_index=0
        )
        log.info("Found test version: {}".format(test_version))

        log.info("Installing Universe version: {}={}".format(package_name, universe_version))
        sdk_install.install(
            package_name,
            service_name,
            expected_running_tasks,
            package_version=universe_version,
            additional_options=additional_options,
            timeout_seconds=timeout_seconds,
            wait_for_deployment=wait_for_deployment,
        )
    finally:
        if universe_version:
            # Return the Universe repo back to the bottom of the repo list so that we can upgrade to
            # the build version.
            universe_version, test_version = sdk_repository.move_universe_repo(package_name)

    log.info("Upgrading {}: {} => {}".format(package_name, universe_version, test_version))
    update_or_upgrade_or_downgrade(
        package_name,
        service_name,
        test_version,
        test_version_additional_options,
        expected_running_tasks,
        wait_for_deployment,
        timeout_seconds,
    )


# In the soak cluster, we assume that the Universe version of the framework is already installed.
# Also, we assume that the Universe is the default repo (at --index=0) and the stub repos are
# already in place, so we don't need to add or remove any repos.
#
# (1) Upgrades to test version of framework.
# (2) Downgrades to Universe version.
def soak_upgrade_downgrade(
    package_name,
    service_name,
    expected_running_tasks,
    additional_options={},
    timeout_seconds=TIMEOUT_SECONDS,
    wait_for_deployment=True,
):
    sdk_cmd.run_cli("package install --cli {} --yes".format(package_name))
    version = "stub-universe"
    log.info("Upgrading to test version: {} {}".format(package_name, version))
    update_or_upgrade_or_downgrade(
        package_name,
        service_name,
        version,
        additional_options,
        expected_running_tasks,
        wait_for_deployment,
        timeout_seconds,
    )

    # Default Universe is at --index=0
    version = sdk_repository._get_pkg_version(package_name)
    log.info("Downgrading to Universe version: {} {}".format(package_name, version))
    update_or_upgrade_or_downgrade(
        package_name,
        service_name,
        version,
        additional_options,
        expected_running_tasks,
        wait_for_deployment,
        timeout_seconds,
    )


@retrying.retry(
    stop_max_attempt_number=15, wait_fixed=10000, retry_on_result=lambda result: result is None
)
def get_config(package_name, service_name):
    """Return the active config for the current service.
    This is retried 15 times, waiting 10s between retries."""
    try:
        # Refrain from dumping the full ServiceSpec to stdout
        rc, stdout, _ = sdk_cmd.svc_cli(
            package_name, service_name, "debug config target", print_output=False
        )
        assert rc == 0, "Target config fetch failed"
        return json.loads(stdout)
    except Exception as e:
        log.error("Could not determine target config: %s", str(e))
        return None


def update_or_upgrade_or_downgrade(
    package_name,
    service_name,
    to_package_version,
    additional_options,
    expected_running_tasks,
    wait_for_deployment=True,
    timeout_seconds=TIMEOUT_SECONDS,
):
    initial_config = get_config(package_name, service_name)
    task_ids = sdk_tasks.get_task_ids(service_name, "")
    if (to_package_version and not is_cli_supports_service_version_upgrade()) or (
        additional_options and not is_cli_supports_service_options_update()
    ):
        log.info(
            "Using marathon flow to upgrade %s to version [%s]", service_name, to_package_version
        )
        sdk_marathon.destroy_app(service_name)
        sdk_install.install(
            package_name,
            service_name,
            expected_running_tasks,
            additional_options=additional_options,
            package_version=to_package_version,
            timeout_seconds=timeout_seconds,
            wait_for_deployment=wait_for_deployment,
        )
    else:
        _update_service_with_cli(package_name, service_name, to_package_version, additional_options)
    return not wait_for_deployment or _wait_for_deployment(
        package_name, service_name, initial_config, task_ids, timeout_seconds
    )


def _update_service_with_cli(
    package_name, service_name, to_package_version=None, additional_options=None
):
    update_cmd = ["update", "start"]

    if to_package_version:
        ensure_cli_supports_service_version_upgrade()
        update_cmd.append("--package-version={}".format(to_package_version))
        log.info("Using CLI to upgrade %s to version [%s]", service_name, to_package_version)
    else:
        log.info("Using CLI to update %s", service_name)

    if additional_options:
        ensure_cli_supports_service_options_update()
        options_file = tempfile.NamedTemporaryFile("w")
        json.dump(additional_options, options_file)
        options_file.flush()  # ensure json content is available for the CLI to read below
        update_cmd.append("--options={}".format(options_file.name))
        log.info("Will update '%s' with options: %s", service_name, additional_options)

    rc, _, _ = sdk_cmd.svc_cli(package_name, service_name, " ".join(update_cmd))
    if rc != 0:
        # Since `sdk_cmd.svc_cli` should already have output error details, so we just raise an
        # exception.
        raise Exception("{} update failed".format(service_name))

    if to_package_version:
        # We must manually upgrade the package CLI because it's not done automatically in this flow
        # (and why should it? that'd imply the package CLI replacing itself via a call to the main
        # CLI...)
        sdk_cmd.run_cli(
            "package install --yes --cli --package-version={} {}".format(
                to_package_version, package_name
            )
        )


def _wait_for_deployment(package_name, service_name, initial_config, task_ids, timeout_seconds):
    updated_config = get_config(package_name, service_name)

    if updated_config == initial_config:
        log.info("No config change detected. Tasks should not be restarted")
        sdk_tasks.check_tasks_not_updated(service_name, "", task_ids)
    else:
        log.info("Checking that all tasks have restarted")
        sdk_tasks.check_tasks_updated(service_name, "", task_ids)

    # this can take a while, default is 15 minutes. for example with HDFS, we can hit the expected
    # total task count via ONCE tasks, without actually completing deployment
    log.info(
        "Waiting for package={} service={} to finish deployment plan...".format(
            package_name, service_name
        )
    )
    sdk_plan.wait_for_completed_deployment(service_name, timeout_seconds)


def is_cli_supports_service_version_upgrade():
    """Version upgrades are supported for [EE 1.9+] only"""
    return is_cli_supports_service_options_update() and not sdk_utils.is_open_dcos()


def is_cli_supports_service_options_update():
    """Service updates are supported in [EE 1.9+] or [Open 1.11+]"""
    return sdk_utils.dcos_version_at_least("1.9") and (
        not sdk_utils.is_open_dcos() or sdk_utils.dcos_version_at_least("1.11")
    )


def ensure_cli_supports_service_version_upgrade():
    assert (
        is_cli_supports_service_version_upgrade()
    ), "Version upgrades supported in 1.11+ in Open DC/OS"


def ensure_cli_supports_service_options_update():
    assert (
        is_cli_supports_service_options_update()
    ), "Service updates are supported in [EE] or [Open 1.11+]"
