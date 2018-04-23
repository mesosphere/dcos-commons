"""
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_package_registry IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import json
import logging
import os
import urllib.request
from typing import Callable, Dict, List, Tuple

import retrying
import sdk_cmd
import sdk_install
import sdk_repository
import sdk_security
import sdk_utils

log = logging.getLogger(__name__)

PACKAGE_REGISTRY_NAME = 'package-registry'
PACKAGE_REGISTRY_SERVICE_NAME = 'registry'
PACKAGE_REGISTRY_STUB_URL = 'PACKAGE_REGISTRY_STUB_URL'


def add_package_registry_stub() -> Dict:
    # TODO Remove this method, install from bootstrap registry.
    if PACKAGE_REGISTRY_STUB_URL not in os.environ:
        raise Exception(
            '{} is not found in env.'.format(PACKAGE_REGISTRY_STUB_URL)
        )
    stub_url = os.environ[PACKAGE_REGISTRY_STUB_URL]
    with urllib.request.urlopen(stub_url) as url:
        repo = json.loads(url.read().decode())
        min_supported = [
            x for x in repo['packages'] if x['name'] == PACKAGE_REGISTRY_NAME
        ][0]['minDcosReleaseVersion']

    if sdk_utils.dcos_version_less_than(min_supported):
        raise Exception(
            'Min DC/OS {} required for package registry'.format(min_supported)
        )
    return sdk_repository.add_stub_universe_urls([stub_url])


def install_package_registry(service_secret_path: str) -> Dict:
    # Install Package Registry
    # wait_for_deployment is `False` because the deployment checks do not apply
    # to package registry as it is not an SDK app.
    sdk_install.install(
        PACKAGE_REGISTRY_NAME,
        PACKAGE_REGISTRY_SERVICE_NAME,
        expected_running_tasks=0,
        additional_options={
            'registry': {
                'service-account-secret-path': service_secret_path
            }
        },
        wait_for_deployment=False,
        insert_strict_options=False
    )
    log.info(
        '{} with name {} installed successfully; ensuring its writable'.format(
            PACKAGE_REGISTRY_NAME, PACKAGE_REGISTRY_SERVICE_NAME
        )
    )

    pkg_reg_repo = sdk_repository.add_stub_universe_urls([
        'https://{}.marathon.l4lb.thisdcos.directory/repo'.format(
            PACKAGE_REGISTRY_SERVICE_NAME
        )
    ])
    # If `describe` endpoint is working, registry is writable by AR.
    package_name = 'hello'
    package_version = 'world'
    expected_msg = 'Version [{}] of package [{}] ' \
                   'not found'.format(package_version, package_name)
    wait_until_cli_condition(
        'registry describe --package-name={} --package-version={}'.format(
            package_name, package_version
        ),
        lambda code, out, err: code == 1 and expected_msg in err
    )
    return pkg_reg_repo


def add_dcos_files_to_registry(
        tmpdir_factory  # _pytest.TempdirFactory
) -> None:
    # Use DCOS_FILES_PATH if its set to a valid path OR use pytest's tmpdir.
    dcos_files_path = os.environ.get('DCOS_FILES_PATH', '')
    valid_path_set = os.path.isdir(dcos_files_path)
    if valid_path_set and not os.access(dcos_files_path, os.W_OK):
        log.warning('{} is not writable.'.format(dcos_files_path))
        valid_path_set = False
    if not valid_path_set:
        dcos_files_path = str(tmpdir_factory.mktemp(sdk_utils.random_string()))
    stub_universe_urls = sdk_repository.get_universe_repos()
    log.info('Using {} to build .dcos files (if not exists) from {}'.format(
        dcos_files_path, stub_universe_urls
    ))
    dcos_files_list = build_dcos_files_from_stubs(
        stub_universe_urls,
        dcos_files_path,
        tmpdir_factory
    )
    log.info('Bundled .dcos files : {}'.format(dcos_files_list))
    for file_path, name, version in dcos_files_list:
        rc, out, err = sdk_cmd.run_raw_cli(' '.join([
            'registry',
            'add',
            '--dcos-file={}'.format(file_path),
            '--json'
        ]))
        assert rc == 0
        assert len(json.loads(out)['packages']) > 0, 'No packages were added'
        wait_until_cli_condition(
            ' '.join([
                'registry',
                'describe',
                '--package-name=' + name,
                '--package-version=' + version,
                '--json'
            ]),
            lambda code, out, err: code == 0 and
            json.loads(out).get('status') == 'Added'
        )


@retrying.retry(
    stop_max_delay=5 * 60 * 1000,
    wait_fixed=5 * 1000
)
def wait_until_cli_condition(
        cmd: str,
        check: Callable[[int, str, str], bool]
) -> None:
    code, stdout, stderr = sdk_cmd.run_raw_cli(cmd)
    assert check(code, stdout, stderr)


def build_dcos_files_from_stubs(
        stub_universe_urls: List,
        dcos_files_path: str,
        tmpdir_factory  # _pytest.TempdirFactory
) -> List[Tuple[str, str, str]]:
    if not len(stub_universe_urls):
        return stub_universe_urls
    package_file_paths = []
    for repo_url in stub_universe_urls:
        headers = {
            "User-Agent": "dcos/{}".format(sdk_utils.dcos_version()),
            "Accept": "application/vnd.dcos.universe.repo+json;"
                      "charset=utf-8;version={}".format('v4'),
        }
        req = urllib.request.Request(repo_url, headers=headers)
        with urllib.request.urlopen(req) as f:
            data = json.loads(f.read().decode())
            for package in data['packages']:
                package_file_paths.append(
                    build_dcos_file_from_universe_definition(
                        package,
                        dcos_files_path,
                        tmpdir_factory
                    )
                )
    return package_file_paths


def build_dcos_file_from_universe_definition(
        package: Dict,
        dcos_files_path: str,
        tmpdir_factory  # _pytest.TempdirFactory
) -> Tuple[str, str, str]:
    """
    Build the .dcos file if its not already present in the given directory.
    Returns a Tuple containing (path of .dcos file, name, and version)
    """
    # TODO Ideally we should `migrate` and then `build`.
    name = package['name']
    version = package['version']
    target = os.path.join(dcos_files_path, '{}-{}.dcos'.format(name, version))
    if os.path.isfile(target):
        log.info('Skipping build, using cached file : {}'.format(target))
    else:
        del package['releaseVersion']
        del package['selected']
        package_json_file = tmpdir_factory\
            .mktemp(sdk_utils.random_string())\
            .join(sdk_utils.random_string())
        package_json_file.write(json.dumps(package))
        rc, _, _ = sdk_cmd.run_raw_cli(' '.join([
            'registry',
            'build',
            '--build-definition-file={}'.format(str(package_json_file)),
            '--output-directory={}'.format(dcos_files_path),
            '--json'
        ]))
        assert rc == 0
    assert os.path.isfile(target), 'No valid .dcos file is built'
    return target, name, version


def grant_perms_for_registry_account(service_uid: str) -> None:
    # Grant only required permissions to registry
    perms = 'dcos:adminrouter:ops:ca:rw'
    rc, _, _ = sdk_cmd.run_raw_cli(' '.join([
        'security',
        'org',
        'users',
        'grant',
        service_uid,
        perms,
        'full'
    ]))
    assert rc == 0, 'Required perms [{}] could not be obtained for {}'.format(
        perms, service_uid
    )


def package_registry_session(tmpdir_factory):  # _pytest.TempdirFactory
    pkg_reg_stub = {}
    pkg_reg_repo = {}
    try:
        # TODO Remove stub. We should install from bootstrap registry.
        pkg_reg_stub = add_package_registry_stub()
        service_uid = 'pkg-reg-uid-{}'.format(sdk_utils.random_string())
        secret_path = '{}-secret-{}'.format(
            service_uid,
            sdk_utils.random_string()
        )
        sdk_security.create_service_account(service_uid, secret_path)
        grant_perms_for_registry_account(service_uid)
        pkg_reg_repo = install_package_registry(secret_path)
        add_dcos_files_to_registry(tmpdir_factory)
        yield
    finally:
        log.info('Teardown of package_registry_session initiated')
        sdk_repository.remove_universe_repos(pkg_reg_repo)
        # TODO If/when adding S3 backend, remove `Added` packages.
        sdk_install.uninstall(
            PACKAGE_REGISTRY_NAME,
            PACKAGE_REGISTRY_SERVICE_NAME
        )
        sdk_repository.remove_universe_repos(pkg_reg_stub)
        # No need to revoke perms, just delete the secret.
        sdk_security.delete_service_account(service_uid, secret_path)
