#!/usr/bin/python

import difflib
import hashlib
import os
import os.path
import re
import shutil
import sys
import tempfile
import time
import zipfile


class UniversePackageBuilder(object):

    def __init__(self, package_name, package_version, input_dir_path, upload_dir_url, artifact_paths):
        self.__pkg_name = package_name
        self.__pkg_version = package_version
        self.__input_dir_path = input_dir_path
        self.__upload_dir_url = upload_dir_url

        if not os.path.isdir(input_dir_path):
            raise Exception('Provided package path is not a directory: {}'.format(input_dir_path))

        self.__artifact_files = {}
        for artifact_path in artifact_paths:
            if not os.path.isfile(artifact_path):
                raise Exception('Provided package path is not a file: {} (full list: {})'.format(artifact_path, artifact_paths))
            prior_path = self.__artifact_files.get(os.path.basename(artifact_path), '')
            if prior_path:
                raise Exception('Duplicate filename between "{}" and "{}". Artifact filenames must be unique.'.format(prior_path, artifact_path))
            self.__artifact_files[os.path.basename(artifact_path)] = artifact_path


    def __create_version_json(self, metadir):
        version = open(os.path.join(metadir, 'version.json'), 'w')
        version.write('''{
  "version": "2.0.0"
}
''')
        version.flush()
        version.close()


    def __create_index_json(self, metadir):
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
            'ver': self.__pkg_version,
            'name': self.__pkg_name,
            'time': time.ctime()})
        index.flush()
        index.close()


    def __create_tree(self, scratchdir):
        treedir = os.path.join(scratchdir, 'stub-universe-{}'.format(self.__pkg_name))
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
        self.__create_version_json(metadir)
        self.__create_index_json(metadir)
        # create stub-universe/repo/packages/P/package/0/[*.json*]
        pkgdir = os.path.join(treedir, 'repo', 'packages', self.__pkg_name[0].upper(), self.__pkg_name, '0')
        os.makedirs(pkgdir)
        for pkgfile in os.listdir(self.__input_dir_path):
            shutil.copyfile(os.path.join(self.__input_dir_path, pkgfile), os.path.join(pkgdir, pkgfile))
        return treedir


    def __calculate_sha256(self, filepath):
        BLOCKSIZE = 65536
        hasher = hashlib.sha256()
        with open(filepath, 'rb') as fd:
            buf = fd.read(BLOCKSIZE)
            while len(buf) > 0:
                hasher.update(buf)
                buf = fd.read(BLOCKSIZE)
        return hasher.hexdigest()


    def __apply_templating_file(self, filepath):
        if not '.json' in os.path.basename(filepath):
            # just in case, avoid processing eg a huge binary file
            print('Ignoring non-json file: {}'.format(filepath))
            return
        orig_content = open(filepath, 'r').read()
        # version:
        new_content = orig_content.replace('{{package-version}}', self.__pkg_version)
        # artifact dir:
        new_content = new_content.replace('{{artifact-dir}}', self.__upload_dir_url)
        # sha256 (relies on artifact paths being passed via cmdline args):
        shafilenames = re.findall('{{sha256:(.+)}}', new_content)
        for filename in shafilenames:
            path = self.__artifact_files.get(filename, '')
            if not path:
                raise Exception('Unable to find path for artifact file named "{}" (for sha256). All artifacts must be passed as arguments.'.format(filename))
            # use % replacement since "".format() is confused by the extra {/}'s:
            new_content = new_content.replace('{{sha256:%s}}' % filename, self.__calculate_sha256(path))
        if orig_content == new_content:
            print('No templating detected in {}'.format(filepath))
        else:
            print('Applied templating changes to {}:'.format(filepath))
            print('\n'.join(difflib.ndiff(orig_content.split('\n'), new_content.split('\n'))))
        rewrite = open(filepath, 'w')
        rewrite.write(new_content)
        rewrite.flush()
        rewrite.close()


    def __apply_templating_tree(self, scratchdir):
        for root, dirs, files in os.walk(scratchdir):
            files.sort() # nice to have: process in consistent order
            for f in files:
                self.__apply_templating_file(os.path.join(root, f))


    def __create_zip(self, scratchdir):
        # if compression is enabled, cosmos returns 'invalid stored block lengths'.
        # this only happens with python-generated files, mutual format incompatibility?:
        zippath = os.path.join(scratchdir, 'stub-universe-{}.zip'.format(self.__pkg_name))
        zipout = zipfile.ZipFile(zippath, 'w', zipfile.ZIP_STORED)
        for root, dirs, files in os.walk(scratchdir):
            destroot = root[len(scratchdir):]
            print('  adding: {}/ => {}/'.format(root, destroot))
            # cosmos requires a preceding explicit directory entry:
            zipout.write(root, destroot)
            files.sort() # nice to have: process in consistent order
            for f in files:
                srcpath = os.path.join(root, f)
                if srcpath == zippath:
                    # don't include the (newly created) zipfile itself!
                    continue
                destpath = srcpath[len(scratchdir):]
                print('  adding: {} => {}'.format(srcpath, destpath))
                zipout.write(srcpath, destpath)
        return zippath


    def build_zip(self):
        '''builds a universe zip and returns its location on disk'''
        scratchdir = tempfile.mkdtemp(prefix='stub-universe-tmp')
        treedir = self.__create_tree(scratchdir)
        self.__apply_templating_tree(scratchdir)
        zippath = self.__create_zip(scratchdir)
        shutil.rmtree(treedir)
        return zippath


def print_help(argv):
    print('Syntax: {} <package-name> <package-version> <template-package-dir> <artifact-base-path> [artifact files ...]'.format(argv[0]))
    print('  Example: $ $0 kafka 1.2.3-4.5.6 /path/to/template/jsons/ https://example.com/path/to/kafka-artifacts /path/to/artifact1.zip /path/to/artifact2.zip /path/to/artifact3.zip'.format(argv[0]))


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
    print('''###
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
    print('---')
    print('Built Stub Universe package:')
    # print the package location as the last line of stdout (used by ci-upload.sh):
    print(package_path)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
