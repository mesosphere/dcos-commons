#!/usr/bin/env python3
#
# Uploads artifacts to S3.
# Produces a universe, and uploads it to S3.
#
# Env:
#   S3_BUCKET (default: infinity-artifacts)
#   S3_DIR_PATH (default: autdelete7d)
#   S3_URL (default: s3://${S3_BUCKET}/${S3_DIR_PATH}/<pkg_name>/<random>
#   ARTIFACT_DIR (default: ...s3.amazonaws.com...)
#     Base HTTP dir to use when rendering links
import logging
import os
import os.path
import random
import string
import sys
import time

import universe

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


class AWSPublisher(object):

    def __init__(
            self,
            package_name,
            package_version,
            input_dir_path,
            artifact_paths):
        self._dry_run = os.environ.get('DRY_RUN', '')
        self._pkg_name = package_name
        self._pkg_version = package_version
        self._input_dir_path = input_dir_path

        self._universe_url_prefix = os.environ.get(
            'UNIVERSE_URL_PREFIX',
            'https://universe-converter.mesosphere.com/transform?url=')

        if not os.path.isdir(input_dir_path):
            raise Exception('Provided package path is not a directory: {}'.format(input_dir_path))

        self._artifact_paths = []
        for artifact_path in artifact_paths:
            if not os.path.isfile(artifact_path):
                err = 'Provided package path is not a file: {} (full list: {})'.format(artifact_path, artifact_paths)
                raise Exception(err)
            self._artifact_paths.append(artifact_path)

        s3_directory_url, self._http_directory_url = s3_urls_from_env(self._pkg_name)
        self._uploader = universe.S3Uploader(s3_directory_url, self._dry_run)

    def _spam_universe_url(self, universe_url):
        # write jenkins properties file to $WORKSPACE/<pkg_version>.properties:
        jenkins_workspace_path = os.environ.get('WORKSPACE', '')
        if jenkins_workspace_path:
            properties_file = open(os.path.join(jenkins_workspace_path, '{}.properties'.format(self._pkg_version)), 'w')
            properties_file.write('STUB_UNIVERSE_URL={}\n'.format(universe_url))
            properties_file.write('STUB_UNIVERSE_S3_DIR={}\n'.format(self._uploader.get_s3_directory()))
            properties_file.flush()
            properties_file.close()
        # write URL to provided text file path:
        universe_url_path = os.environ.get('UNIVERSE_URL_PATH', '')
        if universe_url_path:
            universe_url_file = open(universe_url_path, 'w')
            universe_url_file.write('{}\n'.format(universe_url))
            universe_url_file.flush()
            universe_url_file.close()

    def upload(self):
        '''generates a unique directory, then uploads artifacts and a new stub universe to that directory'''
        package_info = universe.Package(self._pkg_name, self._pkg_version)
        package_manager = universe.PackageManager(dry_run=self._dry_run)
        builder = universe.UniversePackageBuilder(
            package_info, package_manager,
            self._input_dir_path, self._http_directory_url, self._artifact_paths,
            self._dry_run)
        universe_path = builder.build_package()

        # upload universe package definition first and get its URL
        self._uploader.upload(
            universe_path,
            content_type='application/vnd.dcos.universe.repo+json;charset=utf-8')
        universe_url = self._universe_url_prefix + self._http_directory_url + '/' + os.path.basename(universe_path)
        logger.info('---')
        logger.info('STUB UNIVERSE: {}'.format(universe_url))
        logger.info('---')
        logger.info('Uploading {} artifacts:'.format(len(self._artifact_paths)))

        for path in self._artifact_paths:
            self._uploader.upload(path)

        self._spam_universe_url(universe_url)

        # print to stdout, while the rest is all stderr:
        print(universe_url)

        logger.info('---')
        logger.info('(Re)install your package using the following commands:')
        logger.info('dcos package uninstall {}'.format(self._pkg_name))
        logger.info('\n- - - -\nFor 1.9 or older clusters only')
        logger.info('dcos node ssh --master-proxy --leader ' +
                    '"docker run mesosphere/janitor /janitor.py -r {0}-role -p {0}-principal -z dcos-service-{0}"'.format(self._pkg_name))
        logger.info('- - - -\n')
        logger.info('dcos package repo remove {}-aws'.format(self._pkg_name))
        logger.info('dcos package repo add --index=0 {}-aws {}'.format(self._pkg_name, universe_url))
        logger.info('dcos package install --yes {}'.format(self._pkg_name))

        return universe_url

def s3_urls_from_env(package_name):
    s3_bucket = os.environ.get('S3_BUCKET', 'infinity-artifacts')
    logger.info('Using artifact bucket: {}'.format(s3_bucket))

    s3_dir_path = os.environ.get('S3_DIR_PATH', 'autodelete7d')
    s3_dir_name = os.environ.get('S3_DIR_NAME')
    if not s3_dir_name:
        s3_dir_name = '{}-{}'.format(
            time.strftime("%Y%m%d-%H%M%S"),
            ''.join([random.SystemRandom().choice(string.ascii_letters + string.digits) for i in range(16)]))

    # sample s3_directory: 'infinity-artifacts/autodelete7d/kafka/20160815-134747-S6vxd0gRQBw43NNy'
    s3_directory_url = os.environ.get(
            'S3_URL',
            's3://{}/{}/{}/{}'.format(
            s3_bucket,
            s3_dir_path,
            package_name,
            s3_dir_name))

    http_directory_url = os.environ.get(
            'ARTIFACT_DIR',
            'https://{}.s3.amazonaws.com/{}/{}/{}'.format(
            s3_bucket,
            s3_dir_path,
            package_name,
            s3_dir_name))
    
    return s3_directory_url, http_directory_url

def print_help(argv):
    logger.info('Syntax: {} <package-name> <template-package-dir> [artifact files ...]'.format(argv[0]))
    logger.info('  Example: $ {} kafka /path/to/universe/jsons/ /path/to/artifact1.zip /path/to/artifact2.zip /path/to/artifact3.zip'.format(argv[0]))
    logger.info('In addition, environment variables named \'TEMPLATE_SOME_PARAMETER\' will be inserted against the provided package template (with params of the form \'{{some-parameter}}\')')


def main(argv):
    if len(argv) < 3:
        print_help(argv)
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
###'''.format(package_name, package_version, package_dir_path, '\n'.join(['- {}'.format(path) for path in artifact_paths])))

    AWSPublisher(package_name, package_version, package_dir_path, artifact_paths).upload()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
