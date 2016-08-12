#!/usr/bin/python

import base64
import difflib
import json
import os
import os.path
import pprint
import re
import shutil
import sys
import tempfile
import urllib
import urllib2
import zipfile

try:
    from http.client import HTTPSConnection
except ImportError:
    # Python 2
    from httplib import HTTPSConnection

class UniverseReleaseBuilder(object):

    def __init__(self, package_version, stub_universe_url,
                 min_dcos_release_version = os.environ.get('MIN_DCOS_RELEASE_VERSION', '1.7'),
                 upload_dryrun = False,
                 push_dryrun = False):
        name_match = re.match('.+/stub-universe-(.+).zip$', stub_universe_url)
        if not name_match:
            raise Exception('Unable to extract package name from stub universe URL. ' +
                            'Expected filename of form \'stub-universe-[pkgname].zip\'')
        self.__pkg_name = name_match.group(1)
        self.__pkg_version = package_version
        self.__stub_universe_url = stub_universe_url
        self.__min_dcos_release_version = min_dcos_release_version

        self.__pr_title = 'Release {} {} (automated commit)\n\n'.format(
            self.__pkg_name, self.__pkg_version)
        self.__release_artifact_http_dir = 'https://downloads.mesosphere.com/{}/assets/{}'.format(
            self.__pkg_name, self.__pkg_version)
        self.__release_artifact_s3_dir = 's3://downloads.mesosphere.io/{}/assets/{}'.format(
            self.__pkg_name, self.__pkg_version)

        if upload_dryrun:
            self.__upload_dryrun_flags = '--dryrun '
        else:
            self.__upload_dryrun_flags = ''

        self.__push_dryrun = push_dryrun

        # complain early about any missing envvars...
        # avoid uploading a bunch of stuff to prod just to error out later:
        if not 'GITHUB_TOKEN' in os.environ:
            raise Exception('GITHUB_TOKEN is required: Credential to create a PR against Universe')
        encoded_tok = base64.encodestring(os.environ['GITHUB_TOKEN'].encode('utf-8'))
        self.__github_token = encoded_tok.decode('utf-8').rstrip('\n')
        if not 'AWS_ACCESS_KEY_ID' in os.environ or not 'AWS_SECRET_ACCESS_KEY' in os.environ:
            raise Exception('AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY are required: '
                            + 'Credentials to prod AWS for uploading release artifacts')


    def __download_unpack_stub_universe(self, scratchdir):
        local_zip_path = os.path.join(scratchdir, self.__stub_universe_url.split('/')[-1])
        result = urllib2.urlopen(self.__stub_universe_url)
        dlfile = open(local_zip_path, 'wb')
        dlfile.write(result.read())
        dlfile.flush()
        dlfile.close()
        zipin = zipfile.ZipFile(local_zip_path, 'r')
        badfile = zipin.testzip()
        if badfile:
            raise Exception('Bad file {} in downloaded {} => {}'.format(
                badfile, self.__stub_universe_url, local_zip_path))
        zipin.extractall(scratchdir)
        # check for stub-universe-pkgname/repo/packages/P/pkgname/0/:
        pkgdir_path = os.path.join(
            scratchdir,
            'stub-universe-{}'.format(self.__pkg_name),
            'repo',
            'packages',
            self.__pkg_name[0].upper(),
            self.__pkg_name,
            '0')
        if not os.path.isdir(pkgdir_path):
            raise Exception('Didn\'t find expected path {} after unzipping {}'.format(
                pkgdir_path, local_zip_path))
        os.unlink(local_zip_path)
        return pkgdir_path


    def __update_file_content(self, path, orig_content, new_content, showdiff=True):
        if orig_content == new_content:
            print('No changes detected in {}'.format(path))
            # no-op
        else:
            if showdiff:
                print('Applied templating changes to {}:'.format(path))
                print('\n'.join(difflib.ndiff(orig_content.split('\n'), new_content.split('\n'))))
            else:
                print('Applied templating changes to {}'.format(path))
            newfile = open(path, 'w')
            newfile.write(new_content)
            newfile.flush()
            newfile.close()


    def __update_package_get_artifact_source_urls(self, pkgdir):
        # replace package.json:version (smart replace)
        path = os.path.join(pkgdir, 'package.json')
        print('[1/2] Setting package version to {} in {}'.format(self.__pkg_version, path))
        orig_content = open(path, 'r').read()
        content_json = json.loads(orig_content)
        content_json['version'] = self.__pkg_version
        content_json['packagingVersion'] = '3.0'
        if self.__min_dcos_release_version:
            content_json['minDcosReleaseVersion'] = self.__min_dcos_release_version
        # dumps() adds trailing space, fix that:
        new_content_lines = json.dumps(content_json, indent=2, sort_keys=True).split('\n')
        new_content = '\n'.join([line.rstrip() for line in new_content_lines]) + '\n'
        print(new_content)
        # don't bother showing diff, things get rearranged..
        self.__update_file_content(path, orig_content, new_content, showdiff=False)

        # we expect the artifacts to share the same directory prefix as the stub universe zip itself:
        original_artifact_prefix = '/'.join(self.__stub_universe_url.split('/')[:-1])
        print('[2/2] Replacing artifact prefix {} with {}'.format(
            original_artifact_prefix, self.__release_artifact_http_dir))
        original_artifact_urls = []
        for filename in os.listdir(pkgdir):
            path = os.path.join(pkgdir, filename)
            orig_content = open(path, 'r').read()
            found = re.findall('({}/.+)\"'.format(original_artifact_prefix), orig_content)
            original_artifact_urls += found
            new_content = orig_content.replace(original_artifact_prefix, self.__release_artifact_http_dir)
            self.__update_file_content(path, orig_content, new_content)
        return original_artifact_urls


    def __copy_artifacts_s3(self, scratchdir, original_artifact_urls):
        # before we do anything else, verify that the upload directory doesn't already exist, to
        # avoid automatically stomping on a previous release. if you *want* to do this, you must
        # manually delete the destination directory first.
        ret = os.system('aws s3 ls --recursive {}'.format(self.__release_artifact_s3_dir))
        if ret == 0:
            raise Exception('Release artifact destination already exists. ' +
                            'Refusing to continue until destination has been manually removed: ' +
                            'aws s3 rm --dryrun --recursive {}'.format(self.__release_artifact_s3_dir))
        elif ret > 256:
            raise Exception('Failed to check artifact destination presence (code {}). Bad AWS credentials? Exiting early.'.format(ret))
        print('Destination {} doesnt exist, proceeding...'.format(self.__release_artifact_s3_dir))

        for i in range(len(original_artifact_urls)):
            progress = '[{}/{}]'.format(i + 1, len(original_artifact_urls))
            src_url = original_artifact_urls[i]
            filename = src_url.split('/')[-1]

            local_path = os.path.join(scratchdir, filename)
            dest_s3_url = '{}/{}'.format(self.__release_artifact_s3_dir, filename)

            # TODO: this currently downloads the file via http, then uploads it via 'aws s3 cp'.
            # copy directly from src bucket to dest bucket via 'aws s3 cp'? problem: different credentials

            # download the artifact (dev s3, via http)
            print('{} Downloading {} to {}'.format(progress, src_url, local_path))
            if self.__upload_dryrun_flags:
                # create stub file to make 'aws s3 cp --dryrun' happy:
                stub = open(local_path, 'w')
                stub.write('stub')
                stub.flush()
                stub.close()
            else:
                urllib.URLopener().retrieve(src_url, local_path)
            # re-upload the artifact (prod s3, via awscli)
            print('{} Uploading {} to {}'.format(progress, local_path, dest_s3_url))
            ret = os.system('aws s3 cp {}--acl public-read {} {}'.format(
                self.__upload_dryrun_flags, local_path, dest_s3_url))
            if not ret == 0:
                raise Exception(
                    'Failed to upload {} to {}. '.format(local_path, dest_s3_url) +
                    'Partial release directory may need to be cleared manually before retrying. Exiting early.')
            os.unlink(local_path)


    def __create_universe_branch(self, scratchdir, pkgdir):
        branch = 'automated/release_{}_{}_{}'.format(
            self.__pkg_name, self.__pkg_version, base64.b64encode(os.urandom(4)).decode('utf-8').rstrip('='))
        # check out the repo, create a new branch:
        cmds = [
            'cd {}'.format(scratchdir),
            'git clone --depth 1 --branch version-3.x git@github.com:mesosphere/universe',
            'cd universe',
            'git checkout -b {}'.format(branch)]
        ret = os.system(' && '.join(cmds))
        if not ret == 0:
            raise Exception(
                'Failed to create local Universe git branch {}. '.format(branch) +
                'Note that release artifacts were already uploaded to {}, which must be manually deleted before retrying.'.format(self.__release_artifact_s3_dir))
        universe_repo = os.path.join(scratchdir, 'universe')
        repo_pkg_base = os.path.join(
            universe_repo,
            'repo',
            'packages',
            self.__pkg_name[0].upper(),
            self.__pkg_name)
        # find the prior release number:
        lastnum = -1
        for filename in os.listdir(repo_pkg_base):
            if not os.path.isdir(os.path.join(repo_pkg_base, filename)):
                continue
            try:
                num = int(filename)
            except:
                continue
            if num > lastnum:
                lastnum = num
        last_repo_pkg = os.path.join(repo_pkg_base, str(lastnum))
        this_repo_pkg = os.path.join(repo_pkg_base, str(lastnum + 1))
        # copy the stub universe contents into a new release number, while collecting changes:
        os.makedirs(this_repo_pkg)
        removedfiles = os.listdir(last_repo_pkg)
        addedfiles = []
        filediffs = {}
        for filename in os.listdir(pkgdir):
            if not os.path.isfile(os.path.join(pkgdir, filename)):
                continue
            shutil.copyfile(os.path.join(pkgdir, filename), os.path.join(this_repo_pkg, filename))
            if filename in removedfiles:
                # file exists in both new and old: calculate diff
                removedfiles.remove(filename)
                oldfile = open(os.path.join(last_repo_pkg, filename), 'r')
                newfile = open(os.path.join(this_repo_pkg, filename), 'r')
                filediffs[filename] = ''.join(difflib.unified_diff(
                    oldfile.readlines(), newfile.readlines(),
                    fromfile='{}/{}'.format(lastnum, filename),
                    tofile='{}/{}'.format(lastnum + 1, filename)))
            else:
                addedfiles.append(filename)
        # create a user-friendly diff for use in the commit message:
        resultlines = [
            'Changes since revision {}:\n'.format(lastnum),
            '{} files added: [{}]\n'.format(len(addedfiles), ', '.join(addedfiles)),
            '{} files removed: [{}]\n'.format(len(removedfiles), ', '.join(removedfiles)),
            '{} files changed:\n\n'.format(len(filediffs))]
        # surround diff description with quotes to ensure formatting is preserved:
        resultlines.append('```\n')
        filediff_names = filediffs.keys()
        filediff_names.sort()
        for filename in filediff_names:
            resultlines.append(filediffs[filename])
        resultlines.append('```\n')
        commitmsg_path = os.path.join(scratchdir, 'commitmsg.txt')
        commitmsg_file = open(commitmsg_path, 'w')
        commitmsg_file.write(self.__pr_title)
        commitmsg_file.writelines(resultlines)
        commitmsg_file.flush()
        commitmsg_file.close()
        # commit the change and push the branch:
        cmds = [
            'cd {}'.format(os.path.join(scratchdir, 'universe')),
            'git add .',
            'git commit -F {}'.format(commitmsg_path)]
        if not self.__push_dryrun:
            cmds.append('git push origin {}'.format(branch))
        ret = os.system(' && '.join(cmds))
        if not ret == 0:
            raise Exception(
                'Failed to push git branch {} to Universe. '.format(branch) +
                'Note that release artifacts were already uploaded to {}, which must be manually deleted before retrying.'.format(self.__release_artifact_s3_dir))
        return (branch, commitmsg_path)


    def __create_universe_pr(self, branch, commitmsg_path):
        if self.__push_dryrun:
            print('[dry run] Skipping creation of PR against branch {}'.format(branch))
            sys.exit(0)
        headers = {
            'User-Agent': 'release_builder.py',
            'Content-Type': 'application/json',
            'Authorization': 'Basic {}'.format(self.__github_token)}
        payload = {
            'title': self.__pr_title,
            'head': branch,
            'base': 'version-3.x',
            'body': open(commitmsg_path).read()}
        conn = HTTPSConnection('api.github.com')
        conn.set_debuglevel(999)
        conn.request(
            'POST',
            '/repos/mesosphere/universe/pulls',
            body = json.dumps(payload).encode('utf-8'),
            headers = headers)
        return conn.getresponse()


    def release_zip(self):
        scratchdir = tempfile.mkdtemp(prefix='stub-universe-tmp')
        pkgdir = self.__download_unpack_stub_universe(scratchdir)
        original_artifact_urls = self.__update_package_get_artifact_source_urls(pkgdir)
        self.__copy_artifacts_s3(scratchdir, original_artifact_urls)
        (branch, commitmsg_path) = self.__create_universe_branch(scratchdir, pkgdir)
        return self.__create_universe_pr(branch, commitmsg_path)


def print_help(argv):
    print('Syntax: {} <package-version> <stub-universe-url>'.format(argv[0]))
    print('  Example: $ {} 1.2.3-4.5.6 https://example.com/path/to/stub-universe-kafka.zip'.format(argv[0]))
    print('Required credentials in env:')
    print('- AWS S3: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY')
    print('- Github (Personal Access Token): GITHUB_TOKEN')
    print('Required CLI programs:')
    print('- git')
    print('- aws')


def main(argv):
    if len(argv) < 3:
        print_help(argv)
        return 1
    # the package version:
    package_version = argv[1]
    # url where the stub universe is located:
    stub_universe_url = argv[2].rstrip('/')
    print('''###
Universe URL:    {}
Release Version: {}
###'''.format(package_version, stub_universe_url))

    builder = UniverseReleaseBuilder(package_version, stub_universe_url)
    response = builder.release_zip()
    if response.status < 200 or response.status >= 300:
        print('Got {} response to PR creation request:'.format(response.status))
        print('Response:')
        pprint.pprint(response.read())
        print('You will need to manually create the PR against the branch that was pushed above.')
        return -1
    print('---')
    print('Created pull request for version {} (PTAL):'.format(package_version))
    # print the PR location as the last line of stdout (may be used upstream):
    print(json.loads(response.read())['html_url'])
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
