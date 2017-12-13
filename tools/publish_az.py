#!/usr/bin/env python3
#
# Uploads artifacts to Azure Blob Storage.
# Produces a universe, and uploads it to Blob.
# If running in jenkins ($WORKSPACE is defined), writes $WORKSPACE/stub-universe.properties
#
# Env:
#   BLOB_CONTAINER (default: infinity-artifacts)
#   BLOB_DIR_PATH (default: autdelete7d)
#   ARTIFACT_DIR (default: ...blob.core.windows.net...)
#     Base HTTP dir to use when rendering links

import json
import logging
import os
import os.path
import random
import string
import subprocess
import sys
import time

import github_update
import universe_builder

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


class AzureBlobPublisher(object):

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

        storage_account = os.environ.get('AZURE_STORAGE_ACCOUNT', '')
        if storage_account == '':
            err = 'Must specify AZURE_STORAGE_ACCOUNT in env'
            self._github_updater.update('error', err)
            raise Exception(err)
        
        self._blob_container = os.environ.get('BLOB_CONTAINER', 'infinity-artifacts')
        blob_dir_path = os.environ.get('BLOB_DIR_PATH', 'autodelete7d')
        dir_name = '{}-{}'.format(
            time.strftime("%Y%m%d-%H%M%S"),
            ''.join([random.SystemRandom().choice(string.ascii_letters + string.digits) for i in range(16)]))

        self._blob_directory = '{}/{}/{}'.format(
            blob_dir_path,
            self._pkg_name,
            dir_name)

        self._http_directory = os.environ.get(
            'ARTIFACT_DIR',
            'https://{}.blob.core.windows.net/{}/{}'.format(
                storage_account,
                self._blob_container,
                self._blob_directory))

        self._github_updater = github_update.GithubStatusUpdater('upload:{}'.format(package_name))

        if not os.path.isdir(input_dir_path):
            err = 'Provided package path is not a directory: {}'.format(input_dir_path)
            self._github_updater.update('error', err)
            raise Exception(err)

        # check if az cli tools are installed
        cmd = "az --version"
        ret = os.system(cmd)
        if not ret == 0:
            err = 'Required Azure CLI 2.0 tools not installed.'
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


    def _upload_artifact(self, filepath, content_type=None):
        filename = os.path.basename(filepath)
        cmdlist = ['az storage blob upload']
        if content_type:
            cmdlist.append('--content-type "{}"'.format(content_type))
        cmdlist.append('-f {} -c {} -n {}/{} 1>&2'.format(filepath, self._blob_container, self._blob_directory, filename))
        cmd = ' '.join(cmdlist)
        if self._dry_run:
            logger.info('[DRY RUN] {}'.format(cmd))
            ret = 0
        else:
            logger.info(cmd)
            ret = os.system(cmd)
        if not ret == 0:
            err = 'Failed to upload {} to Blob'.format(filename)
            self._github_updater.update('error', err)
            raise Exception(err)
        return '{}/{}'.format(self._http_directory, filename)


    def _spam_universe_url(self, universe_url):
        # write jenkins properties file to $WORKSPACE/<pkg_version>.properties:
        jenkins_workspace_path = os.environ.get('WORKSPACE', '')
        if jenkins_workspace_path:
            properties_file = open(os.path.join(jenkins_workspace_path, '{}.properties'.format(self._pkg_version)), 'w')
            properties_file.write('STUB_UNIVERSE_URL={}\n'.format(universe_url))
            properties_file.flush()
            properties_file.close()
        # write URL to provided text file path:
        universe_url_path = os.environ.get('UNIVERSE_URL_PATH', '')
        if universe_url_path:
            universe_url_file = open(universe_url_path, 'w')
            universe_url_file.write('{}\n'.format(universe_url))
            universe_url_file.flush()
            universe_url_file.close()
        num_artifacts = len(self._artifact_paths)
        if num_artifacts == 1:
            suffix = ''
        else:
            suffix = 's'
        self._github_updater.update(
            'success',
            'Uploaded stub universe and {} artifact{}'.format(num_artifacts, suffix),
            universe_url)


    def upload(self):
        '''generates a unique directory, then uploads artifacts and a new stub universe to that directory'''
        builder = universe_builder.UniversePackageBuilder(
            self._pkg_name, self._pkg_version,
            self._input_dir_path, self._http_directory, self._artifact_paths)
        try:
            universe_path = builder.build_package()
        except Exception as e:
            err = 'Failed to create stub universe: {}'.format(str(e))
            self._github_updater.update('error', err)
            raise

        # print universe url early
        universe_url = self._upload_artifact(universe_path, content_type=builder.content_type())
        logger.info('---')
        logger.info('Uploading {} artifacts:'.format(len(self._artifact_paths)))

        for path in self._artifact_paths:
            self._upload_artifact(path)

        self._spam_universe_url(universe_url)

        # print to stdout, while the rest is all stderr:
        print(universe_url)
        
        # check if container has public access
        try:
            ret = subprocess.check_output(['az', 'storage', 'container', 'show-permission', '-n', self._blob_container])
            permission = json.loads(ret.decode('utf-8'))
            if permission['publicAccess'] == 'off':
                logger.warn('---')
                logger.warn('WARNING: Azure Blob container is not public.')
                logger.warn('Make the container public with the following command:')
                logger.warn('az storage container set-permission -n {} --public-access blob'.format(self._blob_container))
        except Exception as e:
            err = 'Failed to check container permissions: {}'.format(str(e))
            self._github_updater.update('error', err)
            raise

        logger.info('---')
        logger.info('(Re)install your package using the following commands:')
        logger.info('dcos package uninstall {}'.format(self._pkg_name))
        logger.info('\n- - - -\nFor 1.9 or older clusters only')
        logger.info('dcos node ssh --master-proxy --leader ' +
                    '"docker run mesosphere/janitor /janitor.py -r {0}-role -p {0}-principal -z dcos-service-{0}"'.format(self._pkg_name))
        logger.info('- - - -\n')
        logger.info('dcos package repo remove {}-azure'.format(self._pkg_name))
        logger.info('dcos package repo add --index=0 {}-azure {}'.format(self._pkg_name, universe_url))
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
Artifacts:       {}
###'''.format(package_name, package_dir_path, ','.join(artifact_paths)))

    AzureBlobPublisher(package_name, package_dir_path, artifact_paths).upload()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
