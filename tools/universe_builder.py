#!/usr/bin/env python3

import difflib
import hashlib
import logging
import os
import os.path
import re
import shutil
import sys
import tempfile
import time
import zipfile

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")

jre_url = 'https://downloads.mesosphere.com/java/jre-8u121-linux-x64.tar.gz'
jre_jce_unlimited_url = 'https://downloads.mesosphere.com/java/jre-8u112-linux-x64-jce-unlimited.tar.gz'
libmesos_bundle_url = 'https://downloads.mesosphere.com/libmesos-bundle/libmesos-bundle-1.9.0-rc2-1.2.0-rc2-1.tar.gz'

class UniversePackageBuilder(object):

    def __init__(self, package_name, package_version, input_dir_path, upload_dir_url, artifact_paths):
        self._pkg_name = package_name
        self._pkg_version = package_version
        self._input_dir_path = input_dir_path
        self._upload_dir_url = upload_dir_url

        if not os.path.isdir(input_dir_path):
            raise Exception('Provided package path is not a directory: {}'.format(input_dir_path))

        self._artifact_files = {}
        for artifact_path in artifact_paths:
            if not os.path.isfile(artifact_path):
                raise Exception('Provided package path is not a file: {} (full list: {})'.format(artifact_path, artifact_paths))
            prior_path = self._artifact_files.get(os.path.basename(artifact_path), '')
            if prior_path:
                raise Exception('Duplicate filename between "{}" and "{}". Artifact filenames must be unique.'.format(prior_path, artifact_path))
            self._artifact_files[os.path.basename(artifact_path)] = artifact_path


    def _create_version_json(self, metadir):
        version = open(os.path.join(metadir, 'version.json'), 'w')
        version.write('''{
  "version": "2.0.0"
}
''')
        version.flush()
        version.close()


    def _create_index_json(self, metadir):
        index = open(os.path.join(metadir, 'index.json'), 'w')
        template = '''{
  "packages":[ {
    "name": "%(name)s",
    "description": "Test package for %(name)s, generated %(time)s",
    "currentVersion": "%(ver)s",
    "versions": { "%(ver)s":"0" },
    "tags": [ ],
    "framework": true,
    "selected": false
  } ],
  "version":"2.0.0"
}
'''
        # "".format() is confused by all the {}'s, so use % for formatting:
        index.write(template % {
            'ver': self._pkg_version,
            'name': self._pkg_name,
            'time': time.ctime()})
        index.flush()
        index.close()


    def _create_tree(self, scratchdir):
        treedir = os.path.join(scratchdir, 'stub-universe-{}'.format(self._pkg_name))
        # create stub-universe-PKG/scripts/.stub_dir
        scriptsdir = os.path.join(treedir, 'scripts')
        os.makedirs(scriptsdir)
        stubfile = open(os.path.join(scriptsdir, '.stub_dir'), 'w')
        stubfile.write('this a stub directory, install will fail without it.')
        stubfile.flush()
        stubfile.close()
        # create stub-universe-PKG/repo/meta/[version.json|index.json]
        metadir = os.path.join(treedir, 'repo', 'meta')
        os.makedirs(metadir)
        self._create_version_json(metadir)
        self._create_index_json(metadir)
        # create stub-universe/repo/packages/P/package/0/[*.json*]
        pkgdir = os.path.join(treedir, 'repo', 'packages', self._pkg_name[0].upper(), self._pkg_name, '0')
        os.makedirs(pkgdir)
        for pkgfile in os.listdir(self._input_dir_path):
            # Don't copy in .dotfiles; editor temps etc
            if pkgfile.startswith('.'):
                continue
            shutil.copyfile(os.path.join(self._input_dir_path, pkgfile), os.path.join(pkgdir, pkgfile))
        return treedir


    def _calculate_sha256(self, filepath):
        BLOCKSIZE = 65536
        hasher = hashlib.sha256()
        with open(filepath, 'rb') as fd:
            buf = fd.read(BLOCKSIZE)
            while len(buf) > 0:
                hasher.update(buf)
                buf = fd.read(BLOCKSIZE)
        return hasher.hexdigest()


    def _get_file_template_mapping(self, filepath):
        '''Returns a template mapping (dict) for the following cases:
        - Default params like '{{package-version}}' and '{{artifact-dir}}'
        - SHA256 params like '{{sha256:artifact.zip}}' (requires user-provided paths to artifact files)
        - Custom environment params like 'TEMPLATE_SOME_PARAM' which maps to '{{some-param}}'
        '''
        # default template values (may be overridden via eg TEMPLATE_PACKAGE_VERSION envvars):
        template_mapping = {
            'package-version': self._pkg_version,
            'artifact-dir': self._upload_dir_url,
            'jre-url': jre_url,
            'jre-jce-unlimited-url': jre_jce_unlimited_url,
            'libmesos-bundle-url': libmesos_bundle_url}

        # look for any 'sha256:filename' template params, and get shas for those.
        # this avoids calculating shas unless they're requested by the template.
        orig_content = open(filepath, 'r').read()
        for shafilename in re.findall('{{sha256:(.+?)}}', orig_content):
            # somefile.txt => sha256:somefile.txt
            shafilepath = self._artifact_files.get(shafilename, '')
            if not shafilepath:
                raise Exception(
                    'Missing path for artifact file named \'{}\' (to calculate sha256). '.format(shafilename) +
                    'Please provide the full path to this artifact (known artifacts: {})'.format(self._artifact_files))
            template_mapping['sha256:{}'.format(shafilename)] = self._calculate_sha256(shafilepath)

        # import any custom TEMPLATE_SOME_PARAM environment variables:
        for env_key, env_val in os.environ.items():
            if env_key.startswith('TEMPLATE_'):
                # 'TEMPLATE_SOME_KEY' => 'some-key'
                template_mapping[env_key[9:].lower().replace('_','-')] = env_val

        return template_mapping


    def _apply_templating_file(self, filepath):
        # basic checks to avoid files that we shouldn't edit:
        if not filepath.endswith('.json'):
            logger.warning('')
            logger.warning('Ignoring non-json file: {}'.format(filepath))
            return
        if os.stat(filepath).st_size > (1024 * 1024):
            logger.warning('')
            logger.warning('Ignoring file larger than 1MB: {}'.format(filepath))
            return

        template_mapping = self._get_file_template_mapping(filepath)
        orig_content = open(filepath, 'r').read()
        new_content = orig_content
        for template_key, template_val in template_mapping.items():
            new_content = new_content.replace('{{%s}}' % template_key, template_val)
        if orig_content == new_content:
            logger.info('')
            logger.info('No templating detected in {}, leaving file as-is'.format(filepath))
            return
        logger.info('')
        logger.info('Applied templating changes to {}:'.format(filepath))
        logger.info('Template params used:')
        template_keys = list(template_mapping.keys())
        template_keys.sort()
        for key in template_keys:
            logger.info('  {{%s}} => %s' % (key, template_mapping[key]))
        logger.info('Resulting diff:')
        logger.info('\n'.join(difflib.ndiff(orig_content.split('\n'), new_content.split('\n'))))
        rewrite = open(filepath, 'w')
        rewrite.write(new_content)
        rewrite.flush()
        rewrite.close()


    def _apply_templating_tree(self, scratchdir):
        for root, dirs, files in os.walk(scratchdir):
            files.sort() # nice to have: process in consistent order
            for f in files:
                self._apply_templating_file(os.path.join(root, f))


    def _create_zip(self, scratchdir):
        # if compression is enabled, cosmos returns 'invalid stored block lengths'.
        # this only happens with python-generated files, mutual format incompatibility?:
        zippath = os.path.join(scratchdir, 'stub-universe-{}.zip'.format(self._pkg_name))
        zipout = zipfile.ZipFile(zippath, 'w', zipfile.ZIP_STORED)
        for root, dirs, files in os.walk(scratchdir):
            destroot = root[len(scratchdir):]
            logger.info('  adding: {}/ => {}/'.format(root, destroot))
            # cosmos requires a preceding explicit directory entry:
            zipout.write(root, destroot)
            files.sort() # nice to have: process in consistent order
            for f in files:
                srcpath = os.path.join(root, f)
                if srcpath == zippath:
                    # don't include the (newly created) zipfile itself!
                    continue
                destpath = srcpath[len(scratchdir):]
                logger.info('  adding: {} => {}'.format(srcpath, destpath))
                zipout.write(srcpath, destpath)
        return zippath


    def build_zip(self):
        '''builds a universe zip and returns its location on disk'''
        scratchdir = tempfile.mkdtemp(prefix='stub-universe-tmp')
        treedir = self._create_tree(scratchdir)
        self._apply_templating_tree(scratchdir)
        zippath = self._create_zip(scratchdir)
        shutil.rmtree(treedir)
        return zippath


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

    builder = UniversePackageBuilder(
        package_name, package_version, package_dir_path, upload_dir_url, artifact_paths)
    package_path = builder.build_zip()
    if not package_path:
        return -1
    logger.info('---')
    logger.info('Built stub universe package:')
    # print the package location as stdout (the rest of the file is stderr):
    print(package_path)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
