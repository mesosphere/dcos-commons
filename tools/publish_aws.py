#!/usr/bin/env python3
#
# Uploads artifacts to S3.
# Produces a universe, and uploads it to S3.
# If running in jenkins ($WORKSPACE is defined), writes $WORKSPACE/stub-universe.properties
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
            input_dir_path,
            artifact_paths,
            package_version = 'stub-universe'):
        self._dry_run = os.environ.get('DRY_RUN', '')
        self._pkg_name = package_name
        self._pkg_version = package_version
        self._input_dir_path = input_dir_path

        self._aws_region = os.environ.get('AWS_UPLOAD_REGION', '')
        self._universe_url_prefix = os.environ.get(
            'UNIVERSE_URL_PREFIX',
            'https://universe-converter.mesosphere.com/transform?url=')
        s3_bucket = os.environ.get('S3_BUCKET', 'infinity-artifacts')
        s3_dir_path = os.environ.get('S3_DIR_PATH', 'autodelete7d')
        dir_name = '{}-{}'.format(
            time.strftime("%Y%m%d-%H%M%S"),
            ''.join([random.SystemRandom().choice(string.ascii_letters + string.digits) for i in range(16)]))

        # sample s3_directory: 'infinity-artifacts/autodelete7d/kafka/20160815-134747-S6vxd0gRQBw43NNy'
        self._s3_directory = os.environ.get(
            'S3_URL',
            's3://{}/{}/{}/{}'.format(
                s3_bucket,
                s3_dir_path,
                self._pkg_name,
                dir_name))

        self._http_directory = os.environ.get(
            'ARTIFACT_DIR',
            'https://{}.s3.amazonaws.com/{}/{}/{}'.format(
                s3_bucket,
                s3_dir_path,
                self._pkg_name,
                dir_name))

        if not os.path.isdir(input_dir_path):
            raise Exception('Provided package path is not a directory: {}'.format(input_dir_path))

        # check if aws cli tools are installed
        cmd = "aws --version"
        ret = os.system(cmd)
        if not ret == 0:
            raise Exception('Required AWS cli tools not installed.')

        self._artifact_paths = []
        for artifact_path in artifact_paths:
            if not os.path.isfile(artifact_path):
                err = 'Provided package path is not a file: {} (full list: {})'.format(artifact_path, artifact_paths)
                raise Exception(err)
            self._artifact_paths.append(artifact_path)


    def _upload_artifact(self, filepath, content_type=None):
        filename = os.path.basename(filepath)
        cmdlist = ['aws s3']
        if self._aws_region:
            cmdlist.append('--region={}'.format(self._aws_region))
        cmdlist.append('cp --acl public-read')
        if content_type:
            cmdlist.append('--content-type "{}"'.format(content_type))
        cmdlist.append('{} {}/{} 1>&2'.format(filepath, self._s3_directory, filename))
        cmd = ' '.join(cmdlist)
        if self._dry_run:
            logger.info('[DRY RUN] {}'.format(cmd))
            ret = 0
        else:
            logger.info(cmd)
            ret = os.system(cmd)
        if not ret == 0:
            raise Exception('Failed to upload {} to S3'.format(filename))
        return '{}/{}'.format(self._http_directory, filename)


    def _spam_universe_url(self, universe_url):
        # write jenkins properties file to $WORKSPACE/<pkg_version>.properties:
        jenkins_workspace_path = os.environ.get('WORKSPACE', '')
        if jenkins_workspace_path:
            properties_file = open(os.path.join(jenkins_workspace_path, '{}.properties'.format(self._pkg_version)), 'w')
            properties_file.write('STUB_UNIVERSE_URL={}\n'.format(universe_url))
            properties_file.write('STUB_UNIVERSE_S3_DIR={}\n'.format(self._s3_directory))
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
        package_manager = universe.PackageManager()
        builder = universe.UniversePackageBuilder(
            package_info, package_manager,
            self._input_dir_path, self._http_directory, self._artifact_paths)
        universe_path = builder.build_package()

        # upload universe package definition first and get its URL
        universe_url = self._universe_url_prefix + self._upload_artifact(
            universe_path,
            content_type='application/vnd.dcos.universe.repo+json;charset=utf-8')
        logger.info('---')
        logger.info('STUB UNIVERSE: {}'.format(universe_url))
        logger.info('---')
        logger.info('Uploading {} artifacts:'.format(len(self._artifact_paths)))

        for path in self._artifact_paths:
            self._upload_artifact(path)

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
    # local path where the package template is located:
    package_dir_path = argv[2].rstrip('/')
    # artifact paths (to upload along with stub universe)
    artifact_paths = argv[3:]
    logger.info('''###
Package:         {}
Template path:   {}
Artifacts:
{}
###'''.format(package_name, package_dir_path, '\n'.join(['- {}'.format(path) for path in artifact_paths])))

    AWSPublisher(package_name, package_dir_path, artifact_paths).upload()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
