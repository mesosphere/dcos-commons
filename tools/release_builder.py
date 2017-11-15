#!/usr/bin/env python3

import base64
import collections
import difflib
import http.client
import io
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

# For compatibility across DC/OS releases, stub universes are typically advertised against the
# universe-converter. If we fetch the stub universe content via universe-converter, we will just get
# a 400 error because we aren't providing it with a DC/OS version. Therefore we strip out this
# universe-converter URL prefix in order to access the underlying stub-universe file directly
universe_converter_url_prefix = 'https://universe-converter.mesosphere.com/transform?url='

# Indexes to use in universe repo (compare to 49 releases of kafka+beta-kafka in 2+ years):
# - legacy: 0 to 999, leaving room to allow for e.g. 'alpha' or low-priority releases
# - beta: 1,000 to 9,999, no hops between betas (..., 1084, 1085, ...)
beta_index_range = (1000, 10000)
beta_index_increase = 1
# - ga: 10,000 to 99,999, hop by 100 between GAs to allow for hotfixes (..., 10300, 10400, ...)
#       (hotfixes are meanwhile handled by the releaser manually specifying the desired RELEASE_INDEX)
ga_index_range = (10000, 100000)
ga_index_increase = 100


class UniverseReleaseBuilder(object):

    def __init__(
            self,
            package_version,
            stub_universe_url,
            commit_desc='',
            http_release_server=os.environ.get('HTTP_RELEASE_SERVER', 'https://downloads.mesosphere.com'),
            s3_release_bucket=os.environ.get('S3_RELEASE_BUCKET', 'downloads.mesosphere.io'),
            release_docker_image=os.environ.get('RELEASE_DOCKER_IMAGE'),
            release_dir_path=os.environ.get('RELEASE_DIR_PATH', ''),
            beta_release=os.environ.get('BETA', 'False'),
            release_index=os.environ.get('RELEASE_INDEX', '')):
        self._dry_run = os.environ.get('DRY_RUN', '')
        self._force_upload = os.environ.get('FORCE_ARTIFACT_UPLOAD', '').lower() == 'true'
        self._beta_release = beta_release.lower() == 'true'
        self._release_universe_repo=os.environ.get('RELEASE_UNIVERSE_REPO', 'mesosphere/universe')
        self._release_branch=os.environ.get('RELEASE_BRANCH', 'version-3.x')

        if release_index:
            self._release_index = int(release_index)
        else:
            self._release_index = -1

        name_match = re.match('.+/stub-universe-(.+).(zip|json)$', stub_universe_url)
        if not name_match:
            raise Exception('Unable to extract package name from stub universe URL. ' +
                            'Expected filename of form \'stub-universe-[pkgname].zip\' or \'stub-universe-[pkgname].json\'')

        self._stub_universe_pkg_name = name_match.group(1)
        # always omit 'beta-' prefix from released package name, regardless of beta=true/false
        # we no longer use this prefix to release betas, instead using an index range
        if self._stub_universe_pkg_name.startswith('beta-'):
            self._pkg_name = self._stub_universe_pkg_name[len('beta-'):]
        else:
            self._pkg_name = self._stub_universe_pkg_name

        # update package version to include '-beta' suffix if beta was enabled
        if self._beta_release:
            if package_version.endswith('-beta'):
                self._pkg_version = package_version
            else:
                # helpfully add a '-beta' since the user likely just forgot:
                self._pkg_version = package_version + '-beta'
        else:
            # complain if version has '-beta' suffix but BETA mode was disabled:
            if package_version.endswith('-beta'):
                raise Exception(
                    'Requested package version {} ends with "-beta", but BETA mode is disabled. '
                    'Either remove the "-beta" suffix, or enable BETA mode.'.format(package_version))
            else:
                self._pkg_version = package_version

        if stub_universe_url.startswith(universe_converter_url_prefix):
            # universe converter will return an HTTP 400 error because we aren't a DC/OS cluster. get the raw file instead.
            self._stub_universe_url = stub_universe_url[len(universe_converter_url_prefix):]
        else:
            self._stub_universe_url = stub_universe_url

        if not release_dir_path:
            # use package name (without 'beta-' prefix per above) for release artifact directory
            release_dir_path = self._pkg_name + '/assets'

        # automatically include source universe URL in commit description:
        if commit_desc:
            self._commit_desc = '{}\n\nSource URL: {}'.format(commit_desc.rstrip('\n'), self._stub_universe_url)
        else:
            self._commit_desc = 'Source URL: {}'.format(self._stub_universe_url)

        if self._beta_release:
            release_type = 'BETA'
        else:
            release_type = 'GA'
        self._pr_title = '[{}] Release {} {} (automated commit)\n\n'.format(
            release_type, self._pkg_name, self._pkg_version)
        self._release_artifact_http_dir = '{}/{}/{}'.format(
            http_release_server, release_dir_path, self._pkg_version)
        self._release_artifact_s3_dir = 's3://{}/{}/{}'.format(
            s3_release_bucket, release_dir_path, self._pkg_version)
        self._release_docker_image = release_docker_image or None

        # complain early about any missing envvars...
        # avoid uploading a bunch of stuff to prod just to error out later:
        if self._dry_run:
            self._github_token = 'DRY_RUN'
        else:
            if 'GITHUB_TOKEN' not in os.environ:
                raise Exception('GITHUB_TOKEN is required: Credential to create a PR against Universe')
            encoded_tok = base64.encodestring(os.environ['GITHUB_TOKEN'].encode('utf-8'))
            self._github_token = encoded_tok.decode('utf-8').rstrip('\n')

        logger.info('''###
Source URL:      {}
Package name:    {}
Package version: {}
Artifact output: {}
###'''.format(self._stub_universe_url, self._pkg_name, self._pkg_version, self._release_artifact_http_dir))


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
        '''Unpacks a universe-2.x format stub-universe.zip file.
        This format is deprecated in favor of universe-3.x+ json files.'''
        zipin = zipfile.ZipFile(io.BytesIO(stub_universe_file.read()), 'r')
        badfile = zipin.testzip()
        if badfile:
            raise Exception('Failed to unpack {} in downloaded {}'.format(
                badfile, self._stub_universe_url))
        zipin.extractall(scratchdir)
        # check for (and return) path to stub-universe-pkgname/repo/packages/P/pkgname/0/:
        pkgdir = os.path.join(
            scratchdir,
            'stub-universe-{}'.format(self._stub_universe_pkg_name),
            'repo',
            'packages',
            self._stub_universe_pkg_name[0].upper(),
            self._stub_universe_pkg_name,
            '0')
        if not os.path.isdir(pkgdir):
            raise Exception('Didn\'t find expected path {} after unzipping {}'.format(
                pkgdir, self._stub_universe_url))
        return pkgdir


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
                with open(os.path.join(pkgdir, name + '.json'), 'w') as fileref:
                    content = json.dumps(file_dict, indent=2)
                    fileref.write(content)
                    if not content.endswith('\n'):
                        fileref.write('\n')
        extract_json_file(package_json, 'command')
        extract_json_file(package_json, 'config')
        extract_json_file(package_json, 'resource')

        marathon_json = package_json.get('marathon', {}).get('v2AppMustacheTemplate')
        if marathon_json is not None:
            del package_json['marathon']
            with open(os.path.join(pkgdir, 'marathon.json.mustache'), 'w') as marathon_file:
                marathon_file.write(base64.standard_b64decode(marathon_json).decode())

        if 'releaseVersion' in package_json:
            del package_json['releaseVersion']

        with open(os.path.join(pkgdir, 'package.json'), 'w') as package_file:
            json.dump(package_json, package_file, indent=2)

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
            with open(path, 'w') as newfile:
                newfile.write(new_content)


    def _get_and_update_artifact_urls(self, pkgdir):
        '''Rewrites all artifact urls in pkgdir to
        self.release_artifact_http_dir.  Returns the original urls.

        '''
        # we expect the artifacts to share the same directory prefix as the stub universe file itself:
        original_artifact_prefix = '/'.join(self._stub_universe_url.split('/')[:-1])
        logger.info('Replacing artifact prefix {} with {}'.format(
            original_artifact_prefix, self._release_artifact_http_dir))
        original_artifact_urls = []
        # find all URLs, across all json files, which match the directory of the stub universe file:
        # TODO(nickbp): once command.json is finally gone, this could just check resource.json.
        for filename in os.listdir(pkgdir):
            path = os.path.join(pkgdir, filename)
            with open(path, 'r') as orig_file:
                orig_content = orig_file.read()
                found = re.findall('({}/.+)\"'.format(original_artifact_prefix), orig_content)
                original_artifact_urls += found
                new_content = orig_content.replace(original_artifact_prefix, self._release_artifact_http_dir)
                self._update_file_content(path, orig_content, new_content)
        return original_artifact_urls


    def _copy_artifacts_s3(self, scratchdir, original_artifact_urls):
        # Before we do anything else, ensure that we aren't automatically stomping on a previous
        # release by checking that the upload directory doesn't already exist. If you *want* to
        # overwrite a previous release, you must manually go into S3 and delete the destination
        # directory before running this script.
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
                with open(local_path, 'w') as stub:
                    stub.write('stub')
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


    def _find_release_index(self, repo_pkg_base):
        '''Returns the correct number/id for this release in the universe tree, and the prior release to diff against.

        Returns a tuple containing two ints: [prior_index (or -1 if N/A), this_index]'''
        if self._release_index >= 0:
            # set search range to only the specified id: [id]
            id_search_range = (self._release_index, self._release_index + 1)
            id_search_step = 1
        elif self._beta_release:
            # beta index range: [beta_min, beta_min+1, ..., beta_max-2, beta_max-1]
            id_search_range = beta_index_range
            id_search_step = beta_index_increase
        else:
            # GA index range: [ga_min, ga_min+100, ..., ga_max-200, ga_max-100]
            id_search_range = ga_index_range
            id_search_step = ga_index_increase

        # find the next available number within the specified range:
        this_index = -1
        for num in range(id_search_range[0], id_search_range[1], id_search_step):
            if not os.path.exists(os.path.join(repo_pkg_base, str(num))):
                this_index = num
                break
        # if we didn't find an available index within the specified range, give up
        if this_index == -1:
            raise Exception('Unable to find available index within range {} in directory {}: {}'.format(
                id_search_range, repo_pkg_base, sorted(os.listdir(repo_pkg_base))))

        # now, search backwards from this_index to find a prior release to diff against
        # we just want to find an effectively "prior" release for diffing purposes, even if that
        # release is within an earlier release or even release range
        # for example:
        # - if a package goes from beta-only to GA, we want to show the diff vs the last beta
        # - if a new GA is released, we want to show the diff vs the latest existing GA release (including any hotfix release)
        last_index = -1
        for num in reversed(range(0, this_index)): # reversed([0, ..., this-1]) => [this-1, ..., 0]
            if os.path.isdir(os.path.join(repo_pkg_base, str(num))):
                last_index = num
                break

        return (last_index, this_index)


    def _create_universe_branch(self, scratchdir, pkgdir):
        branch = 'automated/release_{}_{}_{}'.format(
            self._pkg_name, self._pkg_version, base64.b64encode(os.urandom(4)).decode('utf-8').rstrip('='))

        # check out the repo, create a new local branch:
        ret = os.system(' && '.join([
            'cd {}'.format(scratchdir),
            'git clone --depth 1 --branch {} git@github.com:{} universe'.format(self._release_branch, self._release_universe_repo),
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

        if not os.path.exists(repo_pkg_base):
            os.makedirs(repo_pkg_base)

        # find the prior and desired release number:
        (last_index, this_index) = self._find_release_index(repo_pkg_base)

        # copy the stub universe contents into a new release number, while calculating diffs:
        last_dir = os.path.join(repo_pkg_base, str(last_index))
        this_dir = os.path.join(repo_pkg_base, str(this_index))
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
                        fromfile='{}/{}'.format(last_index, filename),
                        tofile='{}/{}'.format(this_index, filename)))
                    if filediff:
                        filediffs[filename] = filediff
        else:
            filediffs = {}
            removed_files = {}
            added_files = os.listdir(this_dir)

        # create a user-friendly diff for use in the commit message:
        resultlines = [
            'Changes between revisions {} => {}:\n'.format(last_index, this_index),
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
        with open(commitmsg_path, 'w') as commitmsg_file:
            commitmsg_file.write(self._pr_title)
            commitmsg_file.writelines(resultlines)
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
        with open(commitmsg_path) as commitmsg_file:
            payload = {
                'title': self._pr_title,
                'head': branch,
                'base': self._release_branch,
                'body': commitmsg_file.read()}
        conn = http.client.HTTPSConnection('api.github.com')
        conn.set_debuglevel(999)
        conn.request(
            'POST',
            '/repos/{}/pulls'.format(self._release_universe_repo),
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


    def _update_package_json(self, pkgdir):
        '''Updates the package.json definition to contain the desired version string,
        and updates the package to reflect any beta or non-beta status as necessary.
        '''
        package_file_name = os.path.join(pkgdir, 'package.json')
        with open(package_file_name) as f:
            package_json = json.load(f, object_pairs_hook=collections.OrderedDict)
        orig_package_json = package_json.copy()

        # For beta releases, always clear 'selected'
        if self._beta_release:
            package_json['selected'] = False

        # Update package's name to remove 'beta-' if present
        package_json['name'] = self._pkg_name
        # Update package's version (from 'stub-universe') to reflect the user's input
        package_json['version'] = self._pkg_version

        logger.info('Updated package.json:')
        logger.info('\n'.join(difflib.ndiff(
            json.dumps(orig_package_json, indent=4).split('\n'),
            json.dumps(package_json, indent=4).split('\n'))))

        # Update package.json with changes:
        with open(package_file_name, 'w') as f:
            json.dump(package_json, f, indent=4)
            f.write('\n')


    def _update_marathon_json(self, pkgdir):
        '''Updates the marathon.json definition to contain the desired name and version strings.
        '''
        # note: the file isn't valid JSON, so we edit the raw content instead
        marathon_file_name = os.path.join(pkgdir, 'marathon.json.mustache')
        with open(marathon_file_name) as f:
            orig_marathon_lines = f.readlines()

        marathon_lines = []
        for line in orig_marathon_lines:
            name_match = re.match(r'^ *"PACKAGE_NAME": ?"(.*)",?$', line.rstrip('\n'))
            version_match = re.match(r'^ *"PACKAGE_VERSION": ?"(.*)",?$', line.rstrip('\n'))
            if name_match:
                line = line.replace(name_match.group(1), self._pkg_name)
            elif version_match:
                line = line.replace(version_match.group(1), self._pkg_version)
            marathon_lines.append(line)

        logger.info('Updated marathon.json.mustache:')
        logger.info(''.join(difflib.ndiff(orig_marathon_lines, marathon_lines)))

        # Update marathon.json.mustache with changes:
        with open(marathon_file_name, 'w') as f:
            f.writelines(marathon_lines)


    def release_package(self):
        scratchdir = tempfile.mkdtemp(prefix='stub-universe-tmp')
        pkgdir = self._download_unpack_stub_universe(scratchdir)
        self._update_package_json(pkgdir)
        self._update_marathon_json(pkgdir)

        original_artifact_urls = self._get_and_update_artifact_urls(pkgdir)
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
    logger.info('- RELEASE_INDEX: for hotfixes, a specific package index (else autodetect based on BETA)')
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
