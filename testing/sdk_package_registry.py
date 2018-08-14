"""
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_package_registry IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import json
import logging
import os
from typing import Dict

import retrying
import sdk_cmd
import sdk_install
import sdk_repository
import sdk_security
import sdk_utils

log = logging.getLogger(__name__)

PACKAGE_REGISTRY_NAME = "package-registry"
PACKAGE_REGISTRY_SERVICE_NAME = "registry"


def install_package_registry(service_secret_path: str) -> Dict:
    # Install Package Registry
    # wait_for_deployment is `False` because the deployment checks do not apply
    # to package registry as it is not an SDK app.
    sdk_install.install(
        PACKAGE_REGISTRY_NAME,
        PACKAGE_REGISTRY_SERVICE_NAME,
        expected_running_tasks=0,
        additional_options={"registry": {"service-account-secret-path": service_secret_path}},
        wait_for_deployment=False,
        insert_strict_options=False,
    )
    log.info(
        "{} with name {} installed successfully; ensuring its writable".format(
            PACKAGE_REGISTRY_NAME, PACKAGE_REGISTRY_SERVICE_NAME
        )
    )

    pkg_reg_repo = sdk_repository.add_stub_universe_urls(
        ["https://{}.marathon.l4lb.thisdcos.directory/repo".format(PACKAGE_REGISTRY_SERVICE_NAME)]
    )

    # If `describe` endpoint is working, registry is writable by AR.
    @retrying.retry(stop_max_delay=5 * 60 * 1000, wait_fixed=5 * 1000)
    def wait_for_registry_available():
        code, stdout, stderr = sdk_cmd.run_raw_cli(
            "registry describe --package-name=hello --package-version=world"
        )
        assert code == 1 and "Version [world] of package [hello] not found" in stderr

    wait_for_registry_available()

    return pkg_reg_repo


def add_dcos_files_to_registry() -> None:
    assert (
        "DCOS_FILES_PATH" in os.environ
    ), "DCOS_FILES_PATH has to be set to a location containing .dcos files"
    dcos_files_path = os.environ.get("DCOS_FILES_PATH")
    assert os.path.isdir(dcos_files_path), "{} is an invalid value for DCOS_FILES_PATH".format(
        dcos_files_path
    )
    dcos_files_path = os.environ.get("DCOS_FILES_PATH")
    dcos_files_list = [
        os.path.join(dcos_files_path, f) for f in os.listdir(dcos_files_path) if f.endswith(".dcos")
    ]
    log.info("List of .dcos files : {}".format(dcos_files_list))

    @retrying.retry(stop_max_delay=5 * 60 * 1000, wait_fixed=5 * 1000)
    def wait_for_added_registry(name, version):
        code, stdout, stderr = sdk_cmd.run_raw_cli(
            "registry describe --package-name={} --package-version={} --json".format(name, version),
            print_output=False,
        )
        assert code == 0 and json.loads(stdout).get("status") == "Added"

    for file_path, name, version in dcos_files_list:
        rc, out, err = sdk_cmd.run_raw_cli("registry add --dcos-file={} --json".format(file_path))
        assert rc == 0
        assert len(json.loads(out)["packages"]) > 0, "No packages were added"
        wait_for_added_registry(name, version)


def grant_perms_for_registry_account(service_uid: str) -> None:
    # Grant only required permissions to registry
    perms = "dcos:adminrouter:ops:ca:rw"
    rc, _, _ = sdk_cmd.run_raw_cli(
        " ".join(["security", "org", "users", "grant", service_uid, perms, "full"])
    )
    assert rc == 0, "Required perms [{}] could not be obtained for {}".format(perms, service_uid)


def package_registry_session():
    bootstrap_pkg_reg_repo = {}
    pkg_reg_repo = {}
    try:
        bootstrap_pkg_reg_repo = sdk_repository.add_stub_universe_urls(
            ["https://{}.component.thisdcos.directory/repo".format(PACKAGE_REGISTRY_SERVICE_NAME)]
        )
        service_uid = "pkg-reg-uid-{}".format(sdk_utils.random_string())
        secret_path = "{}-secret-{}".format(service_uid, sdk_utils.random_string())
        sdk_security.create_service_account(service_uid, secret_path)
        grant_perms_for_registry_account(service_uid)
        pkg_reg_repo = install_package_registry(secret_path)
        add_dcos_files_to_registry()
        yield
    finally:
        log.info("Teardown of package_registry_session initiated")
        sdk_repository.remove_universe_repos(pkg_reg_repo)
        # TODO If/when adding S3 backend, remove `Added` packages.
        sdk_install.uninstall(PACKAGE_REGISTRY_NAME, PACKAGE_REGISTRY_SERVICE_NAME)
        sdk_repository.remove_universe_repos(bootstrap_pkg_reg_repo)
        # No need to revoke perms, just delete the secret.
        sdk_security.delete_service_account(service_uid, secret_path)
