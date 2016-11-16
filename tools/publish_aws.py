#!/usr/bin/python
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

import github_update
import universe_builder

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

        self._github_updater = github_update.GithubStatusUpdater('upload:{}'.format(package_name))

        if not os.path.isdir(input_dir_path):
            err = 'Provided package path is not a directory: {}'.format(input_dir_path)
            self._github_updater.update('error', err)
            raise Exception(err)

        # check if aws cli tools are installed
        cmd = "aws --version"
        ret = os.system(cmd)
        if not ret == 0:
            err = 'Required AWS cli tools not installed.'
            self._github_updater.update('error', err)
            raise Exception(err)

        self._artifact_paths = []
        for artifact_path in artifact_paths:
            if not os.path.isfile(artifact_path):
                err = 'Provided package path is not a file: {} (full list: {})'.format(artifact_path, artifact_paths)
                raise Exception(err)
            if artifact_path in self._artifact_paths:
                err = 'Duplicate filename between "{}" and "{}". Artifact filenames must be unique.'.format(prior_path, artifact_path)
                self._github_updater.update('error', err)
                raise Exception(err)
            self._artifact_paths.append(artifact_path)


    def _upload_artifact(self, filepath):
        filename = os.path.basename(filepath)
        if self._aws_region:
            cmd = 'aws s3 --region={} cp --acl public-read {} {}/{} 1>&2'.format(
                self._aws_region, filepath, self._s3_directory, filename)
        else:
            cmd = 'aws s3 cp --acl public-read {} {}/{} 1>&2'.format(
                filepath, self._s3_directory, filename)
        if self._dry_run:
            logger.info('[DRY RUN] {}'.format(cmd))
            ret = 0
        else:
            logger.info(cmd)
            ret = os.system(cmd)
        if not ret == 0:
            err = 'Failed to upload {} to S3'.format(filename)
            self._github_updater.update('error', err)
            raise Exception(err)
        return '{}/{}'.format(self._http_directory, filename)


    def _spam_universe_url(self, universe_url):
        if 'WORKSPACE' in os.environ:
            # write jenkins properties file to $WORKSPACE/stub-universe.properties:
            properties_file = open(os.path.join(os.environ['WORKSPACE'], '{}.properties'.format(self._pkg_version)), 'w')
            properties_file.write('STUB_UNIVERSE_URL={}\n'.format(universe_url))
            properties_file.write('STUB_UNIVERSE_S3_DIR={}\n'.format(self._s3_directory))
            properties_file.flush()
            properties_file.close()
        custom_universes_path = os.environ.get('CUSTOM_UNIVERSES_PATH', '')
        if custom_universes_path:
            # write custom universes file for dcos-tests:
            custom_universes_file = open(custom_universes_path, 'w')
            properties_file.write('stub {}\n'.format(universe_url))
            properties_file.flush()
            properties_file.close()
        num_artifacts = len(self._artifact_paths)
        if num_artifacts > 1:
            suffix = 's'
        else:
            suffix = ''
        self._github_updater.update(
            'success',
            'Uploaded stub universe and {} artifact{}'.format(num_artifacts, suffix),
            universe_url)


    def upload(self):
        '''generates a unique directory, then uploads artifacts and a new stub universe to that directory'''
        try:
            universe_path = universe_builder.UniversePackageBuilder(
                self._pkg_name, self._pkg_version,
                self._input_dir_path, self._http_directory, self._artifact_paths).build_zip()
        except Exception as e:
            err = 'Failed to create stub universe: {}'.format(str(e))
            self._github_updater.update('error', err)
            raise

        # print universe url early
        universe_url = self._upload_artifact(universe_path)
        logger.info('---')
        logger.info('Built and uploaded stub universe:')
        logger.info(universe_url)
        logger.info('---')
        logger.info('Uploading {} artifacts:'.format(len(self._artifact_paths)))

        for path in self._artifact_paths:
            self._upload_artifact(path)

        self._spam_universe_url(universe_url)

        # print to stdout, while the rest was all stderr:
        print(universe_url)
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
Artifacts:       {}
###'''.format(package_name, package_dir_path, ','.join(artifact_paths)))

    AWSPublisher(package_name, package_dir_path, artifact_paths).upload()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
