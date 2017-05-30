#!/usr/bin/env python3

import base64
import collections
import difflib
import hashlib
import json
import logging
import os
import os.path
import re
import sys
import tempfile


logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")

_jre_url = 'https://downloads.mesosphere.com/java/jre-8u131-linux-x64.tar.gz'
_jre_jce_unlimited_url = 'https://downloads.mesosphere.com/java/jre-8u131-linux-x64-jce-unlimited.tar.gz'
_libmesos_bundle_url = 'https://downloads.mesosphere.com/libmesos-bundle/libmesos-bundle-1.9.0-rc2-1.2.0-rc2-1.tar.gz'

_command_json_filename = 'command.json'
_config_json_filename = 'config.json'
_marathon_json_filename = 'marathon.json.mustache'
_package_json_filename = 'package.json'
_resource_json_filename = 'resource.json'
_expected_package_filenames = [
    _command_json_filename,
    _config_json_filename,
    _marathon_json_filename,
    _package_json_filename,
    _resource_json_filename]


class UniversePackageBuilder(object):

    def __init__(self, package_name, package_version, input_dir_path, upload_dir_url, artifact_paths, packaging_version=3):
        self._cosmos_packaging_version = packaging_version
        self._pkg_name = package_name
        self._pkg_version = package_version
        self._upload_dir_url = upload_dir_url

        if not os.path.isdir(input_dir_path):
            raise Exception('Provided package path is not a directory: {}'.format(input_dir_path))
        self._input_dir_path = input_dir_path

        self._artifact_file_paths = {}
        for artifact_path in artifact_paths:
            if not os.path.isfile(artifact_path):
                raise Exception('Provided package path is not a file: {} (full list: {})'.format(artifact_path, artifact_paths))
            prior_path = self._artifact_file_paths.get(os.path.basename(artifact_path), '')
            if prior_path:
                raise Exception('Duplicate filename between "{}" and "{}". Artifact filenames must be unique.'.format(prior_path, artifact_path))
            self._artifact_file_paths[os.path.basename(artifact_path)] = artifact_path


    def _iterate_package_files(self):
        for package_filename in os.listdir(self._input_dir_path):
            package_filepath = os.path.join(self._input_dir_path, package_filename)
            if os.stat(package_filepath).st_size > (1024 * 1024):
                logger.warning('Ignoring package file larger than 1MB: {}'.format(package_filepath))
                continue
            if package_filename not in _expected_package_filenames:
                logger.warning('Ignoring unrecognized package file: {} (expected one of: {})'.format(
                    package_filepath, ', '.join(_expected_package_filenames)))
                continue
            yield package_filename, open(package_filepath).read()


    def _calculate_sha256(self, filepath):
        BLOCKSIZE = 65536
        hasher = hashlib.sha256()
        with open(filepath, 'rb') as fd:
            buf = fd.read(BLOCKSIZE)
            while len(buf) > 0:
                hasher.update(buf)
                buf = fd.read(BLOCKSIZE)
        return hasher.hexdigest()


    def _get_template_mapping_for_content(self, orig_content):
        '''Returns a template mapping (dict) for the following cases:
        - Default params like '{{package-version}}' and '{{artifact-dir}}'
        - SHA256 params like '{{sha256:artifact.zip}}' (requires user-provided paths to artifact files)
        - Custom environment params like 'TEMPLATE_SOME_PARAM' which maps to '{{some-param}}'
        '''
        # default template values (may be overridden via eg TEMPLATE_PACKAGE_VERSION envvars):
        template_mapping = {
            'package-version': self._pkg_version,
            'artifact-dir': self._upload_dir_url,
            'jre-url': _jre_url,
            'jre-jce-unlimited-url': _jre_jce_unlimited_url,
            'libmesos-bundle-url': _libmesos_bundle_url}

        # look for any 'sha256:filename' template params, and get shas for those.
        # this avoids calculating shas unless they're requested by the template.
        for shafilename in re.findall('{{sha256:(.+?)}}', orig_content):
            # somefile.txt => sha256:somefile.txt
            shafilepath = self._artifact_file_paths.get(shafilename, '')
            if not shafilepath:
                raise Exception(
                    'Missing path for artifact file named \'{}\' (to calculate sha256). '.format(shafilename) +
                    'Please provide the full path to this artifact (known artifacts: {})'.format(self._artifact_file_paths))
            template_mapping['sha256:{}'.format(shafilename)] = self._calculate_sha256(shafilepath)

        # import any custom TEMPLATE_SOME_PARAM environment variables:
        for env_key, env_val in os.environ.items():
            if env_key.startswith('TEMPLATE_'):
                # 'TEMPLATE_SOME_KEY' => 'some-key'
                template_mapping[env_key[len('TEMPLATE_'):].lower().replace('_', '-')] = env_val

        return template_mapping


    def _apply_templating_to_file(self, filename, orig_content):
        template_mapping = self._get_template_mapping_for_content(orig_content)
        new_content = orig_content
        for template_key, template_val in template_mapping.items():
            new_content = new_content.replace('{{%s}}' % template_key, template_val)
        if orig_content == new_content:
            logger.info('')
            logger.info('No templating detected in {}, leaving file as-is'.format(filename))
            return orig_content
        logger.info('')
        logger.info('Applied templating changes to {}:'.format(filename))
        logger.info('Template params used:')
        template_keys = list(template_mapping.keys())
        template_keys.sort()
        for key in template_keys:
            logger.info('  {{%s}} => %s' % (key, template_mapping[key]))
        logger.info('Resulting diff:')
        logger.info('\n'.join(difflib.ndiff(orig_content.split('\n'), new_content.split('\n'))))
        return new_content


    def _generate_packages_dict(self, package_files):
        package_json = json.loads(package_files[_package_json_filename], object_pairs_hook=collections.OrderedDict)
        package_json['releaseVersion'] = 0
        package_json['packagingVersion'] = '{}.0'.format(self._cosmos_packaging_version)

        command_json = package_files.get(_command_json_filename)
        if command_json is not None:
            package_json['command'] = json.loads(command_json, object_pairs_hook=collections.OrderedDict)

        config_json = package_files.get(_config_json_filename)
        if config_json is not None:
            package_json['config'] = json.loads(config_json, object_pairs_hook=collections.OrderedDict)

        marathon_json = package_files.get(_marathon_json_filename)
        if marathon_json is not None:
            package_json['marathon'] = {
                'v2AppMustacheTemplate': base64.standard_b64encode(bytearray(marathon_json, 'utf-8')).decode()}

        resource_json = package_files.get(_resource_json_filename)
        if resource_json is not None:
            package_json['resource'] = json.loads(resource_json, object_pairs_hook=collections.OrderedDict)

        return {'packages': [package_json]}


    def build_package(self):
        '''builds a stub universe json package and returns its location on disk'''

        # read files into memory and apply templating to files:
        updated_package_files = {}
        for filename, content in self._iterate_package_files():
            updated_package_files[filename] = self._apply_templating_to_file(filename, content)
        scratchdir = tempfile.mkdtemp(prefix='stub-universe-tmp')
        jsonpath = os.path.join(scratchdir, 'stub-universe-{}.json'.format(self._pkg_name))
        jsonfile = open(jsonpath, 'w')
        jsonfile.write(json.dumps(self._generate_packages_dict(updated_package_files), indent=2))
        jsonfile.flush()
        jsonfile.close()
        return jsonpath


    def content_type(self):
        '''returns the content type that Cosmos requires for stub universes generated by this class'''
        return 'application/vnd.dcos.universe.repo+json;charset=utf-8;version=v{}'.format(
            self._cosmos_packaging_version)


def print_help(argv):
    logger.info('Syntax: {} <package-name> <package-version> <template-package-dir> <artifact-base-path> [artifact files ...]'.format(argv[0]))
    logger.info('  Example: $ {} kafka 1.2.3-4.5.6 /path/to/template/jsons/ https://example.com/path/to/kafka-artifacts /path/to/artifact1.zip /path/to/artifact2.zip /path/to/artifact3.zip'.format(argv[0]))
    logger.info('In addition, environment variables named \'TEMPLATE_SOME_PARAMETER\' will be inserted against the provided package template (with params of the form \'{{some-parameter}}\')')


def main(argv):
    if len(argv) < 5:
        print_help(argv)
        return 1
    # the package name:
    package_name = argv[1]
    # the package version:
    package_version = argv[2]
    # local path where the package template is located:
    package_dir_path = argv[3].rstrip('/')
    # url of the directory where artifacts are located (S3, etc):
    upload_dir_url = argv[4].rstrip('/')
    # artifact paths (for sha256 as needed)
    artifact_paths = argv[5:]
    logger.info('''###
Package:         {} (version {})
Template path:   {}
Upload base dir: {}
Artifacts:       {}
###'''.format(package_name, package_version, package_dir_path, upload_dir_url, ','.join(artifact_paths)))

    package_path = UniversePackageBuilder(
        package_name, package_version, package_dir_path, upload_dir_url, artifact_paths).build_package()
    if not package_path:
        return -1
    logger.info('---')
    logger.info('Built stub universe package:')
    # print the package location as stdout (the rest of the file is stderr):
    print(package_path)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
