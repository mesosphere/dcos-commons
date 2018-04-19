#!/usr/bin/env python3
#
# Uploads .dcos artifacts to S3.
#
# Env:
#   S3_BUCKET (default: infinity-artifacts)
#   S3_DIR_PATH (default: autdelete7d)
#   S3_URL (default: s3://${S3_BUCKET}/${S3_DIR_PATH}/<pkg_name>/<random>
import json
import logging
import os
import random
import shutil
import stat
import string
import subprocess
import sys
import tempfile
import time
import urllib.request

import publish_aws
import universe

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")

_REGISTRY_URL_TEMPLATE = "https://downloads.mesosphere.com/package-registry/" \
                         "binaries/cli/{os}/x86-64/latest/dcos-registry-{os}"


class DCOSFilePublisher(object):

    def __init__(
            self,
            package_name,
            package_version,
            input_dir_path,
            artifact_paths):
        self._dry_run = os.environ.get('DRY_RUN', '')
        self._pkg_name = package_name
        self._pkg_version = package_version
        self._artifact_paths = artifact_paths
        self._input_dir_path = input_dir_path
        self._directory_url = '.'

        if not os.path.isdir(input_dir_path):
            raise Exception(
                'Provided package path is not a directory: {}'.format(
                    input_dir_path
                )
            )

        s3_bucket = os.environ.get('S3_BUCKET', 'infinity-artifacts')
        logger.info('Using artifact bucket: {}'.format(s3_bucket))
        self._s3_bucket = s3_bucket

        s3_dir_path = os.environ.get('S3_DIR_PATH')
        if not s3_dir_path:
            s3_dir_path = os.path.join(
                'autodelete7d',
                '{}-{}'.format(
                    time.strftime("%Y%m%d-%H%M%S"),
                    ''.join([
                        random.choice(string.ascii_letters + string.digits)
                        for _ in range(16)
                    ])
                )
            )

        # Sample S3 file url:
        # Dev : infinity-artifacts/autodelete7d/20160815-134747-S6vxd0gRQBw43NNy/kafka/stub-universe/kafka-stub-universe.dcos
        # Release : infinity-artifacts/permanent/kafka/1.2.3/kafka-1.2.3.dcos
        s3_directory_url = os.environ.get('S3_URL',
                                          's3://{}/{}/{}/{}'.format(
                                              s3_bucket,
                                              s3_dir_path,
                                              package_name,
                                              package_version
                                          ))
        self._uploader = universe.S3Uploader(
            s3_directory_url,
            self._dry_run
        )

    def upload(self):
        with tempfile.TemporaryDirectory() as scratch:
            builder = universe.UniversePackageBuilder(
                universe.Package(self._pkg_name, self._pkg_version),
                universe.PackageManager(dry_run=self._dry_run),
                self._input_dir_path,
                self._directory_url,
                self._artifact_paths,
                self._dry_run
            )
            for filename, content in builder.build_package_files().items():
                with open(os.path.join(scratch, filename), 'w') as f:
                    f.write(content)

            for artifact in self._artifact_paths:
                filename = os.path.basename(artifact)
                shutil.copy2(
                    src=artifact,
                    dst=os.path.join(scratch, filename)
                )

            bundle = migrate_and_build(scratch)
            self._uploader.upload(bundle)
            bundle_url_s3 = os.path.join(
                self._uploader.get_s3_directory(),
                os.path.basename(bundle)
            )
            bundle_url_http = bundle_url_s3.replace(
                's3://{}'.format(self._s3_bucket),
                'https://{}.s3.amazonaws.com'.format(self._s3_bucket)
            )
            logger.info('---')
            logger.info('[S3 URL] DCOS BUNDLE: {}'.format(bundle_url_s3))
            logger.info('DCOS BUNDLE: {}'.format(bundle_url_http))
            logger.info('---')


def migrate_and_build(scratchdir) -> str:
    """
    Migrate the universe catalog files and then build .dcos file
    :param scratchdir: A directory containing the universe definition files
    :return: Absolute path of the .dcos file as string
    """
    # Install Package Registry CLI to `migrate` andThen `build`.
    registry_cmd = './registry'
    cli_path = os.path.abspath(os.path.join(scratchdir, registry_cmd))
    url = get_registry_cli_url()
    logging.info('Installing Registry CLI from {} to {}'.format(url, cli_path))
    # We need DC/OS CLI to be already installed.
    rc, _, _ = run_shell_cmd(["dcos --version"])
    assert rc == 0, 'DC/OS CLI is required to install Registry CLI'
    try:
        urllib.request.urlretrieve(url, cli_path)
        # Make the file executable for current user
        st = os.stat(cli_path)
        os.chmod(cli_path, st.st_mode | stat.S_IEXEC)
    finally:
        assert os.path.isfile(cli_path), 'Registry CLI install failed'
    rc, out, _ = run_shell_cmd([
        registry_cmd,
        'registry',
        'migrate',
        '--package-directory={}'.format(scratchdir),
        '--output-directory={}'.format(scratchdir),
        '--json'
    ], scratchdir)
    assert rc == 0, 'Failed to migrate package definition files'

    rc, out, _ = run_shell_cmd([
        registry_cmd,
        'registry',
        'build',
        '--build-definition-file={}'.format(json.loads(out)['name']),
        '--output-directory={}'.format(scratchdir),
        '--json'
    ], scratchdir)
    assert rc == 0, 'Failed to build dcos file from build definition'
    dcos_file_path = json.loads(out)['name']
    return dcos_file_path


def run_shell_cmd(cmd, cwd=None):
    cmd = [' '.join(cmd)]
    logging.info('CMD : {}'.format(cmd))
    result = subprocess.run(
        cmd,
        shell=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        cwd=cwd
    )
    code = result.returncode
    out = result.stdout.decode('utf-8').strip()
    err = result.stderr.decode('utf-8').strip()
    if out:
        logging.info('STDOUT {}'.format(out))
    if err:
        logging.info('STDERR {}'.format(err))
    return code, out, err


def get_registry_cli_url() -> str:
    # If we need to extend : https://stackoverflow.com/a/13874620/1517133
    platform = sys.platform
    if platform == 'win32':
        platform = 'windows'
    if platform not in ['linux', 'darwin', 'windows']:
        raise RuntimeError('Cannot Install Registry CLI on {}'.format(platform))
    url = _REGISTRY_URL_TEMPLATE.format(os=platform)
    if platform == 'windows':
        url = url + '.exe'
    return url


def main(argv):
    if len(argv) < 3:
        publish_aws.print_help(argv)
        return 1
    # the package name:
    package_name = argv[1]
    # the package version:
    package_version = argv[2]
    # local path where the package template is located:
    package_dir_path = argv[3].rstrip('/')
    # artifact paths (to upload along with stub universe)
    artifact_paths = argv[4:]
    logger.info('''###
Package:         {}
Version:         {}
Template path:   {}
Artifacts:
{}
###'''.format(
        package_name,
        package_version,
        package_dir_path,
        '\n'.join(['- {}'.format(path) for path in artifact_paths])
    ))

    DCOSFilePublisher(
        package_name,
        package_version,
        package_dir_path,
        artifact_paths
    ).upload()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
