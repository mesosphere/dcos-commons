#!/usr/bin/env python3

import base64
import collections
import difflib
import http.client
import json
import logging
import os
import os.path
import pprint
import re
import shutil
import sys
import tempfile
import urllib.request
import zipfile


logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


class UniverseReleaseBuilder(object):

    def __init__(self, package_version, stub_universe_url,
                 commit_desc='',
                 min_dcos_release_version=os.environ.get('MIN_DCOS_RELEASE_VERSION', '1.8'),
                 http_release_server=os.environ.get('HTTP_RELEASE_SERVER', 'https://downloads.mesosphere.com'),
                 s3_release_bucket=os.environ.get('S3_RELEASE_BUCKET', 'downloads.mesosphere.io'),
                 release_docker_image=os.environ.get('RELEASE_DOCKER_IMAGE'),
                 release_dir_path=os.environ.get('RELEASE_DIR_PATH', ''),
                 beta_release=os.environ.get('BETA', 'False')):
        self._dry_run = os.environ.get('DRY_RUN', '')
        self._force_upload = os.environ.get('FORCE_ARTIFACT_UPLOAD', '').lower() == 'true'
        name_match = re.match('.+/stub-universe-(.+).(zip|json)$', stub_universe_url)
        if not name_match:
            raise Exception('Unable to extract package name from stub universe URL. ' +
                            'Expected filename of form \'stub-universe-[pkgname].zip\' or \'stub-universe-[pkgname].json\'')
        self._pkg_name = name_match.group(1)
        if not release_dir_path:
            release_dir_path = self._pkg_name + '/assets'
        self._pkg_version = package_version
        self._commit_desc = commit_desc
        self._stub_universe_url = stub_universe_url
        self._min_dcos_release_version = min_dcos_release_version

        self._pr_title = 'Release {} {} (automated commit)\n\n'.format(
            self._pkg_name, self._pkg_version)
        self._release_artifact_http_dir = '{}/{}/{}'.format(
            http_release_server, release_dir_path, self._pkg_version)
        self._release_artifact_s3_dir = 's3://{}/{}/{}'.format(
            s3_release_bucket, release_dir_path, self._pkg_version)
        self._release_docker_image = release_docker_image or None
        self._beta_release = beta_release.lower() == 'true'

        # complain early about any missing envvars...
        # avoid uploading a bunch of stuff to prod just to error out later:
        if 'GITHUB_TOKEN' not in os.environ:
            raise Exception('GITHUB_TOKEN is required: Credential to create a PR against Universe')
        encoded_tok = base64.encodestring(os.environ['GITHUB_TOKEN'].encode('utf-8'))
        self._github_token = encoded_tok.decode('utf-8').rstrip('\n')


    def _run_cmd(self, cmd, exit_on_fail=True, dry_run_return=0):
        if self._dry_run:
            logger.info('[DRY RUN] {}'.format(cmd))
            return dry_run_return
        else:
            logger.info(cmd)
            ret = os.system(cmd)
            if ret != 0 and exit_on_fail:
                raise Exception("{} return non-zero exit status: {}".format(cmd, ret))
            return ret

    def _unpack_stub_universe_zip(self, scratchdir, stub_universe_file):
        zipin = zipfile.ZipFile(stub_universe_file, 'r')
        badfile = zipin.testzip()
        if badfile:
            raise Exception('Failed to unpack {} in downloaded {}'.format(
                badfile, self._stub_universe_url))
        zipin.extractall(scratchdir)
        # check for (and return) path to stub-universe-pkgname/repo/packages/P/pkgname/0/:
        pkgdir_path = os.path.join(
            scratchdir,
            'stub-universe-{}'.format(self._pkg_name),
            'repo',
            'packages',
            self._pkg_name[0].upper(),
            self._pkg_name,
            '0')
        if not os.path.isdir(pkgdir_path):
            raise Exception('Didn\'t find expected path {} after unzipping {}'.format(
                pkgdir_path, self._stub_universe_url))
        return pkgdir_path

    def _unpack_stub_universe_json(self, scratchdir, stub_universe_file):
        stub_universe_json = json.loads(stub_universe_file.read().decode('utf-8'), object_pairs_hook=collections.OrderedDict)

        # put package files into a subdir of scratchdir: avoids conflicts with reuse of scratchdir elsewhere
        pkgdir = os.path.join(scratchdir, 'stub-universe-{}'.format(self._pkg_name))
        os.makedirs(pkgdir)

        if 'packages' not in stub_universe_json:
            raise Exception('Expected "packages" key in root of stub universe JSON: {}'.format(
                self._stub_universe_url))
        if len(stub_universe_json['packages']) != 1:
            raise Exception('Expected single "packages" entry in stub universe JSON: {}'.format(
                self._stub_universe_url))

        # note: we delete elements from package_json they're unpacked, as package_json is itself written to a file
        package_json = stub_universe_json['packages'][0]

        def extract_json_file(package_dict, name):
            file_dict = package_dict.get(name)
            if file_dict is not None:
                del package_dict[name]
                # ensure that the file has a trailing newline (json.dump() doesn't!)
                fileref = open(os.path.join(pkgdir, name + '.json'), 'w')
                content = json.dumps(file_dict, indent=2)
                fileref.write(content)
                if not content.endswith('\n'):
                    fileref.write('\n')
                fileref.flush()
                fileref.close()
        extract_json_file(package_json, 'command')
        extract_json_file(package_json, 'config')
        extract_json_file(package_json, 'resource')

        marathon_json = package_json.get('marathon', {}).get('v2AppMustacheTemplate')
        if marathon_json is not None:
            del package_json['marathon']
            marathon_file = open(os.path.join(pkgdir, 'marathon.json.mustache'), 'w')
            marathon_file.write(base64.standard_b64decode(marathon_json).decode())
            marathon_file.flush()
            marathon_file.close()

        if 'releaseVersion' in package_json:
            del package_json['releaseVersion']

        json.dump(package_json, open(os.path.join(pkgdir, 'package.json'), 'w'), indent=2)

        return pkgdir

    def _download_unpack_stub_universe(self, scratchdir):
        '''Returns the path to the package directory in the stub universe.'''
        stub_universe_file = urllib.request.urlopen(self._stub_universe_url)

        _, stub_universe_extension = os.path.splitext(self._stub_universe_url)
        if stub_universe_extension == '.zip':
            # stub universe zip package (universe 2.x only)
            return self._unpack_stub_universe_zip(scratchdir, stub_universe_file)
        elif stub_universe_extension == '.json':
            # stub universe json file (universe 3.x+ only)
            return self._unpack_stub_universe_json(scratchdir, stub_universe_file)
        else:
            raise Exception('Expected .zip or .json extension for stub universe: {}'.format(
                self._stub_universe_url))


    def _update_file_content(self, path, orig_content, new_content, showdiff=True):
        if orig_content == new_content:
            logger.info('No changes detected in {}'.format(path))
            # no-op
        else:
            if showdiff:
                logger.info('Applied templating changes to {}:'.format(path))
                logger.info('\n'.join(difflib.ndiff(orig_content.split('\n'), new_content.split('\n'))))
            else:
                logger.info('Applied templating changes to {}'.format(path))
            newfile = open(path, 'w')
            newfile.write(new_content)
            newfile.flush()
            newfile.close()


    def _update_package_get_artifact_source_urls(self, pkgdir):
        '''Rewrites all artifact urls in pkgdir to
        self.release_artifact_http_dir.  Returns the original urls.

        '''
        # replace package.json:version (smart replace)
        path = os.path.join(pkgdir, 'package.json')
        packagingVersion = '3.0'
        if self._min_dcos_release_version == '0':
            minDcosReleaseVersion = None
        else:
            minDcosReleaseVersion = self._min_dcos_release_version
        logger.info('[1/2] Setting version={}, packagingVersion={}, minDcosReleaseVersion={} in {}'.format(
            self._pkg_version, packagingVersion, minDcosReleaseVersion, path))
        orig_content = open(path, 'r').read()
        content_json = json.loads(orig_content)
        content_json['version'] = self._pkg_version
        content_json['packagingVersion'] = packagingVersion
        if minDcosReleaseVersion:
            content_json['minDcosReleaseVersion'] = minDcosReleaseVersion
        # dumps() adds trailing space, fix that:
        new_content_lines = json.dumps(content_json, indent=2, sort_keys=True).split('\n')
        new_content = '\n'.join([line.rstrip() for line in new_content_lines]) + '\n'
        logger.info(new_content)
        # don't bother showing diff, things get rearranged..
        self._update_file_content(path, orig_content, new_content, showdiff=False)

        # we expect the artifacts to share the same directory prefix as the stub universe file itself:
        original_artifact_prefix = '/'.join(self._stub_universe_url.split('/')[:-1])
        logger.info('[2/2] Replacing artifact prefix {} with {}'.format(
            original_artifact_prefix, self._release_artifact_http_dir))
        original_artifact_urls = []
        for filename in os.listdir(pkgdir):
            path = os.path.join(pkgdir, filename)
            orig_content = open(path, 'r').read()
            found = re.findall('({}/.+)\"'.format(original_artifact_prefix), orig_content)
            original_artifact_urls += found
            new_content = orig_content.replace(original_artifact_prefix, self._release_artifact_http_dir)
            self._update_file_content(path, orig_content, new_content)
        return original_artifact_urls


    def _copy_artifacts_s3(self, scratchdir, original_artifact_urls):
        # before we do anything else, verify that the upload directory doesn't already exist, to
        # avoid automatically stomping on a previous release. if you *want* to do this, you must
        # manually delete the destination directory first. (and redirect stdout to stderr)
        cmd = 'aws s3 ls --recursive {} 1>&2'.format(self._release_artifact_s3_dir)
        ret = self._run_cmd(cmd, False, 1)
        if ret == 0:
            if self._force_upload:
                logger.info('Destination {} exists but force upload is configured, proceeding...'.format(self._release_artifact_s3_dir))
            else:
                raise Exception('Release artifact destination already exists. ' +
                                'Refusing to continue until destination has been manually removed:\n' +
                                'Do this: aws s3 rm --dryrun --recursive {}'.format(self._release_artifact_s3_dir))
        elif ret > 256:
            raise Exception('Failed to check artifact destination presence (code {}). Bad AWS credentials? Exiting early.'.format(ret))
        else:
            logger.info('Destination {} doesnt exist, proceeding...'.format(self._release_artifact_s3_dir))

        for i in range(len(original_artifact_urls)):
            progress = '[{}/{}]'.format(i + 1, len(original_artifact_urls))
            src_url = original_artifact_urls[i]
            filename = src_url.split('/')[-1]

            local_path = os.path.join(scratchdir, filename)
            dest_s3_url = '{}/{}'.format(self._release_artifact_s3_dir, filename)

            # download the artifact (dev s3, via http)
            if self._dry_run:
                # create stub file to make 'aws s3 cp --dryrun' happy:
                logger.info('[DRY RUN] {} Downloading {} to {}'.format(progress, src_url, local_path))
                stub = open(local_path, 'w')
                stub.write('stub')
                stub.flush()
                stub.close()
                logger.info('[DRY RUN] {} Uploading {} to {}'.format(progress, local_path, dest_s3_url))
                ret = os.system('aws s3 cp --dryrun --acl public-read {} {} 1>&2'.format(
                    local_path, dest_s3_url))
            else:
                # download the artifact (http url referenced in package)
                logger.info('{} Downloading {} to {}'.format(progress, src_url, local_path))
                urllib.request.URLopener().retrieve(src_url, local_path)
                # re-upload the artifact (prod s3, via awscli)
                logger.info('{} Uploading {} to {}'.format(progress, local_path, dest_s3_url))
                ret = os.system('aws s3 cp --acl public-read {} {} 1>&2'.format(
                    local_path, dest_s3_url))
            if ret != 0:
                raise Exception(
                    'Failed to upload {} to {}. '.format(local_path, dest_s3_url) +
                    'Partial release directory may need to be cleared manually before retrying. Exiting early.')
            os.unlink(local_path)


    def _create_universe_branch(self, scratchdir, pkgdir):
        branch = 'automated/release_{}_{}_{}'.format(
            self._pkg_name, self._pkg_version, base64.b64encode(os.urandom(4)).decode('utf-8').rstrip('='))

        # check out the repo, create a new local branch:
        ret = os.system(' && '.join([
            'cd {}'.format(scratchdir),
            'git clone --depth 1 --branch version-3.x git@github.com:mesosphere/universe',
            'cd universe',
            'git config --local user.email jenkins@mesosphere.com',
            'git config --local user.name release_builder.py',
            'git checkout -b {}'.format(branch)]))
        if ret != 0:
            raise Exception(
                'Failed to create local Universe git branch {}. '.format(branch) +
                'Note that any release artifacts were already uploaded to {}, which must be manually deleted before retrying.'.format(self._release_artifact_s3_dir))
        universe_repo = os.path.join(scratchdir, 'universe')
        repo_pkg_base = os.path.join(
            universe_repo,
            'repo',
            'packages',
            self._pkg_name[0].upper(),
            self._pkg_name)

        # find the prior release number:
        lastnum = -1
        if not os.path.exists(repo_pkg_base):
            os.makedirs(repo_pkg_base)
        for filename in os.listdir(repo_pkg_base):
            if not os.path.isdir(os.path.join(repo_pkg_base, filename)):
                continue
            try:
                num = int(filename)
            except:
                continue
            if num > lastnum:
                lastnum = num

        # copy the stub universe contents into a new release number, while collecting changes:
        last_dir = os.path.join(repo_pkg_base, str(lastnum))
        this_dir = os.path.join(repo_pkg_base, str(lastnum + 1))
        shutil.copytree(pkgdir, this_dir)

        if os.path.exists(last_dir):
            last_dir_files = set(os.listdir(last_dir))
            this_dir_files = set(os.listdir(this_dir))

            removed_files = last_dir_files - this_dir_files
            added_files = this_dir_files - last_dir_files
            filediffs = {}

            shared_files = last_dir_files & this_dir_files
            for filename in shared_files:
                # file exists in both new and old: calculate diff
                last_filename = os.path.join(last_dir, filename)
                this_filename = os.path.join(this_dir, filename)
                with open(last_filename, 'r') as last_file, open(this_filename, 'r') as this_file:
                    filediff = ''.join(difflib.unified_diff(
                        last_file.readlines(), this_file.readlines(),
                        fromfile='{}/{}'.format(lastnum, filename),
                        tofile='{}/{}'.format(lastnum + 1, filename)))
                    if filediff:
                        filediffs[filename] = filediff
        else:
            filediffs = {}
            removed_files = {}
            added_files = os.listdir(this_dir)

        # create a user-friendly diff for use in the commit message:
        resultlines = [
            'Changes since revision {}:\n'.format(lastnum),
            '{} files added: [{}]\n'.format(len(added_files), ', '.join(added_files)),
            '{} files removed: [{}]\n'.format(len(removed_files), ', '.join(removed_files)),
            '{} files changed:\n\n'.format(len(filediffs))]
        if self._commit_desc:
            resultlines.insert(0, 'Description:\n{}\n\n'.format(self._commit_desc))
        # surround diff description with quotes to ensure formatting is preserved:
        resultlines.append('```\n')
        filediff_names = list(filediffs.keys())
        filediff_names.sort()
        for filename in filediff_names:
            resultlines.append(filediffs[filename])
        resultlines.append('```\n')
        commitmsg_path = os.path.join(scratchdir, 'commitmsg.txt')
        commitmsg_file = open(commitmsg_path, 'w')
        commitmsg_file.write(self._pr_title)
        commitmsg_file.writelines(resultlines)
        commitmsg_file.flush()
        commitmsg_file.close()
        # commit the change and push the branch:
        cmds = ['cd {}'.format(os.path.join(scratchdir, 'universe')),
                'git add .',
                'git commit -q -F {}'.format(commitmsg_path)]
        if self._dry_run:
            # ensure the debug goes to stderr...:
            cmds.append('git show -q HEAD 1>&2')
        else:
            cmds.append('git push origin {}'.format(branch))
        ret = os.system(' && '.join(cmds))
        if ret != 0:
            raise Exception(
                'Failed to push git branch {} to Universe. '.format(branch) +
                'Note that any release artifacts were already uploaded to {}, which must be manually deleted before retrying.'.format(self._release_artifact_s3_dir))
        return (branch, commitmsg_path)


    def _create_universe_pr(self, branch, commitmsg_path):
        if self._dry_run:
            logger.info('[DRY RUN] Skipping creation of PR against branch {}'.format(branch))
            return None
        headers = {
            'User-Agent': 'release_builder.py',
            'Content-Type': 'application/json',
            'Authorization': 'Basic {}'.format(self._github_token)}
        payload = {
            'title': self._pr_title,
            'head': branch,
            'base': 'version-3.x',
            'body': open(commitmsg_path).read()}
        conn = http.client.HTTPSConnection('api.github.com')
        conn.set_debuglevel(999)
        conn.request(
            'POST',
            '/repos/mesosphere/universe/pulls',
            body=json.dumps(payload).encode('utf-8'),
            headers=headers)
        return conn.getresponse()


    def _original_docker_image(self, pkgdir):
        resource_filename = os.path.join(pkgdir, 'resource.json')
        with open(resource_filename) as f:
            resource_json = json.load(f, object_pairs_hook=collections.OrderedDict)
            try:
                docker_dict = resource_json['assets']['container']['docker']
                assert len(docker_dict) == 1
                return list(docker_dict.values())[0]
            except KeyError:
                return None

    def _copy_docker_image(self, pkgdir, orig_docker_image):
        assert self._release_docker_image != None

        self._run_cmd('docker pull {}'.format(
            orig_docker_image))
        self._run_cmd('docker tag {} {}'.format(
            orig_docker_image,
            self._release_docker_image))
        self._run_cmd('docker push {}'.format(
            self._release_docker_image))

        resource_filename = os.path.join(pkgdir, 'resource.json')
        with open(resource_filename) as f:
            resource_json = json.load(f, object_pairs_hook=collections.OrderedDict)
            docker_dict = resource_json['assets']['container']['docker']
            key = list(docker_dict.keys())[0]
            docker_dict[key] = self._release_docker_image

        with open(resource_filename, 'w') as f:
            json.dump(resource_json, f, indent=4, sort_keys=True)


    def _add_beta_attributes(self, pkgdir):
        if not self._beta_release:
            return pkgdir

        # Add the beta optin bool to config.json
        config_file_name = os.path.join(pkgdir, 'config.json')
        with open(config_file_name) as f:
            config_json = json.load(f, object_pairs_hook=collections.OrderedDict)
            service_dict = config_json['properties']['service']
            service_dict['properties']['beta-optin'] = {
                "description":"I have been invited to the Beta Program and accept all the terms of the Beta Agreement.",
                "type": "boolean",
                "title": "Agree to Beta terms",
                "default": ""
            }
            required_list = service_dict.setdefault('required', [])
            required_list.append('beta-optin')

        with open(config_file_name, 'w') as f:
            json.dump(config_json, f, indent=4)

        # Add the beta prefix to package.json
        package_file_name = os.path.join(pkgdir, 'package.json')
        with open(package_file_name) as f:
            package_json = json.load(f, object_pairs_hook=collections.OrderedDict)

        package_json['selected'] = False
        package_json['name'] = 'beta-' + package_json['name']

        with open(package_file_name, 'w') as f:
            json.dump(package_json, f, indent=4)

        # Rename the directory structure
        parts = pkgdir.split('/')
        parts[-2] = 'beta-' + parts[-2]
        parts[-3] = 'B'
        beta_pkg_dir = '/'.join(parts)
        self._pkg_name = parts[-2]
        shutil.copytree(pkgdir, beta_pkg_dir)
        shutil.rmtree(pkgdir)
        return beta_pkg_dir


    def release_package(self):
        scratchdir = tempfile.mkdtemp(prefix='stub-universe-tmp')
        pkgdir = self._download_unpack_stub_universe(scratchdir)
        if self._beta_release:
            pkgdir = self._add_beta_attributes(pkgdir)

        original_artifact_urls = self._update_package_get_artifact_source_urls(pkgdir)
        self._copy_artifacts_s3(scratchdir, original_artifact_urls)
        if self._release_docker_image:
            orig_docker_image = self._original_docker_image(pkgdir)
            if not orig_docker_image:
                raise Exception('Release to docker specified, but no docker image found in resource.json')
            self._copy_docker_image(pkgdir, orig_docker_image)
        (branch, commitmsg_path) = self._create_universe_branch(scratchdir, pkgdir)
        return self._create_universe_pr(branch, commitmsg_path)


def print_help(argv):
    logger.info('Syntax: {} <package-version> <stub-universe-url> [commit message]'.format(argv[0]))
    logger.info('  Example: $ {} 1.2.3-4.5.6 https://example.com/path/to/stub-universe-kafka.json'.format(argv[0]))
    logger.info('Required credentials in env:')
    logger.info('- AWS S3: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY')
    logger.info('- Github (Personal Access Token): GITHUB_TOKEN')
    logger.info('Optional params in env:')
    logger.info('- BETA: true/false')
    logger.info('Required CLI programs:')
    logger.info('- git')
    logger.info('- aws')


def main(argv):
    if len(argv) < 3:
        print_help(argv)
        return 1
    # the package version:
    package_version = argv[1]
    # url where the stub universe is located:
    stub_universe_url = argv[2].rstrip('/')
    # commit comment, if any:
    commit_desc = ' '.join(argv[3:])
    if commit_desc:
        comment_info = '\nCommit Message:  {}'.format(commit_desc)
    else:
        comment_info = ''
    logger.info('''###
Release Version: {}
Universe URL:    {}{}
###'''.format(package_version, stub_universe_url, comment_info))

    builder = UniverseReleaseBuilder(package_version, stub_universe_url, commit_desc)
    response = builder.release_package()
    if not response:
        # print the PR location as stdout for use upstream (the rest is all stderr):
        print('[DRY RUN] The pull request URL would appear here.')
        return 0
    if response.status < 200 or response.status >= 300:
        logger.error('Got {} response to PR creation request:'.format(response.status))
        logger.error('Response:')
        logger.error(pprint.pformat(response.read()))
        logger.error('You will need to manually create the PR against the branch that was pushed above.')
        return -1
    logger.info('---')
    logger.info('Created pull request for version {} (PTAL):'.format(package_version))
    # print the PR location as stdout for use upstream (the rest is all stderr):
    print(json.loads(response.read().decode('utf-8'))['html_url'])
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
