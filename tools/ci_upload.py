#!/usr/bin/python

import os
import os.path
import random
import string
import sys
import time
import traceback

import github_update
import universe_builder


class CIUploader(object):

    def __init__(self, package_name, input_dir_path, artifact_paths, package_version = 'stub-universe'):
        self.__dry_run = os.environ.get('DRY_RUN', '')
        self.__pkg_name = package_name
        self.__pkg_version = package_version
        self.__input_dir_path = input_dir_path

        self.__aws_region = os.environ.get('AWS_UPLOAD_REGION', '')
        s3_bucket = os.environ.get('S3_BUCKET', 'infinity-artifacts')
        s3_dir_path = os.environ.get('S3_DIR_PATH', 'autodelete7d')
        dir_name = '{}-{}'.format(
            time.strftime("%Y%m%d-%H%M%S"),
            ''.join([random.SystemRandom().choice(string.ascii_letters + string.digits) for i in range(16)]))
        # sample s3_directory: 'infinity-artifacts/autodelete7d/kafka/20160815-134747-S6vxd0gRQBw43NNy'
        self.__s3_directory = 's3://{}/{}/{}/{}'.format(s3_bucket, s3_dir_path, self.__pkg_name, dir_name)
        self.__http_directory = 'https://{}.s3.amazonaws.com/{}/{}/{}'.format(s3_bucket, s3_dir_path, self.__pkg_name, dir_name)

        self.__github_updater = github_update.GithubStatusUpdater('upload:{}'.format(package_name))

        if not os.path.isdir(input_dir_path):
            err = 'Provided package path is not a directory: {}'.format(input_dir_path)
            self.__github_updater.update('error', err)
            raise Exception(err)

        self.__artifact_paths = []
        for artifact_path in artifact_paths:
            if not os.path.isfile(artifact_path):
                err = 'Provided package path is not a file: {} (full list: {})'.format(artifact_path, artifact_paths)
                raise Exception(err)
            if artifact_path in self.__artifact_paths:
                err = 'Duplicate filename between "{}" and "{}". Artifact filenames must be unique.'.format(prior_path, artifact_path)
                self.__github_updater.update('error', err)
                raise Exception(err)
            self.__artifact_paths.append(artifact_path)


    def __upload_artifact(self, filepath):
        filename = os.path.basename(filepath)
        if self.__aws_region:
            cmd = 'aws s3 --region={} cp --acl public-read {} {}/{}'.format(
                self.__aws_region, filepath, self.__s3_directory, filename)
        else:
            cmd = 'aws s3 cp --acl public-read {} {}/{}'.format(
                filepath, self.__s3_directory, filename)
        if self.__dry_run:
            print('[DRY RUN] {}'.format(cmd))
            ret = 0
        else:
            print(cmd)
            ret = os.system(cmd)
        if not ret == 0:
            err = 'Failed to upload {} to S3'.format(filename)
            self.__github_updater.update('error', err)
            raise Exception(err)
        return '{}/{}'.format(self.__http_directory, filename)


    def __spam_universe_url(self, universe_url):
        if os.environ.get('JENKINS_HOME', ''):
            # write jenkins properties file:
            properties_file = open(os.path.join(os.environ['WORKSPACE'], 'stub-universe.properties'), 'w')
            properties_file.write('STUB_UNIVERSE_URL={}\n'.format(universe_url))
            properties_file.write('STUB_UNIVERSE_S3_DIR={}\n'.format(self.__s3_directory))
            properties_file.flush()
            properties_file.close()
        custom_universes_path = os.environ.get('CUSTOM_UNIVERSES_PATH', '')
        if custom_universes_path:
            # write custom universes file for dcos-tests:
            custom_universes_file = open(custom_universes_path, 'w')
            properties_file.write('stub {}\n'.format(universe_url))
            properties_file.flush()
            properties_file.close()
        num_artifacts = len(self.__artifact_paths)
        if num_artifacts > 1:
            suffix = 's'
        else:
            suffix = ''
        self.__github_updater.update(
            'success',
            'Uploaded stub universe and {} artifact{}'.format(num_artifacts, suffix),
            universe_url)


    def upload(self):
        '''generates a unique directory, then uploads artifacts and a new stub universe to that directory'''
        try:
            universe_path = universe_builder.UniversePackageBuilder(
                self.__pkg_name, self.__pkg_version,
                self.__input_dir_path, self.__http_directory, self.__artifact_paths).build_zip()
        except Exception as e:
            traceback.format_exc()
            err = 'Failed to create stub universe: {}'.format(str(e))
            self.__github_updater.update('error', err)
            raise Exception(err)

        # print universe url early
        universe_url = self.__upload_artifact(universe_path)
        print('---')
        print('Built and uploaded stub universe:')
        print(universe_url)
        print('---')
        print('Uploading {} artifacts:'.format(len(self.__artifact_paths)))

        for path in self.__artifact_paths:
            self.__upload_artifact(path)

        self.__spam_universe_url(universe_url)

        return universe_url


def print_help(argv):
    print('Syntax: {} <package-name> <template-package-dir> [artifact files ...]'.format(argv[0]))
    print('  Example: $ {} kafka /path/to/template/jsons/ /path/to/artifact1.zip /path/to/artifact2.zip /path/to/artifact3.zip'.format(argv[0]))
    print('In addition, environment variables named \'TEMPLATE_SOME_PARAMETER\' will be inserted against the provided package template (with params of the form \'{{some-parameter}}\')')


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
    print('''###
Package:         {}
Template path:   {}
Artifacts:       {}
###'''.format(package_name, package_dir_path, ','.join(artifact_paths)))

    CIUploader(package_name, package_dir_path, artifact_paths).upload()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
