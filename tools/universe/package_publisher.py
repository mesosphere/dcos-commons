#!/usr/bin/env python3

import base64
import difflib
import http.client
import json
import logging
import os
import shutil

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")

# By default, publish GA releases at 0, 100, 200, 300, ... to allow hotfixes within releases.
ga_index_multiplier = 100
# Don't bother with skipping indexes for beta releases, just go sequentially.
beta_index_multiplier = 1


class UniversePackagePublisher(object):
    '''Creates a PR for a release against the Universe repository at http://github.com/mesosphere/universe.
    '''

    def __init__(self, package_name, package_version, commit_desc, beta_release, dry_run=False):
        self._pkg_name = package_name
        self._pkg_version = package_version
        self._pr_title = 'Release {} {} (automated commit)\n\n'.format(self._pkg_name, self._pkg_version)
        self._commit_desc = commit_desc
        self._beta_release = beta_release
        self._dry_run = dry_run

        # Optional configuration via envvars:
        self._release_branch = os.environ.get('RELEASE_BRANCH', 'version-3.x')
        self._release_index = int(os.environ.get('RELEASE_INDEX', -1))
        self._github_user = os.environ.get('GITHUB_USER', 'mesosphere-ci')
        self._github_token = os.environ.get('GITHUB_TOKEN', None)
        if self._github_token is None:
            if self._dry_run:
                self._github_token = 'DRY_RUN'
            else:
                raise Exception('GITHUB_TOKEN is required: Credential to create a PR against Universe')
        self._enc_github_token = base64.encodestring(self._github_token.encode('utf-8')).decode('utf-8').rstrip('\n')
        self._release_universe_repo = os.environ.get('RELEASE_UNIVERSE_REPO', 'mesosphere/universe')

    def _find_release_index(self, repo_pkg_base):
        '''Returns the correct number/id for this release in the universe tree, and the prior release to diff against.

        Returns a tuple containing two ints: [prior_index (or -1 if N/A), this_index]'''

        # Find the index to be used for this release:
        last_index = -1
        if self._release_index >= 0:
            if os.path.exists(os.path.join(repo_pkg_base, str(self._release_index))):
                raise Exception('Requested index {} is already occupied in directory {}: {}'.format(
                    self._release_index, repo_pkg_base, sorted(os.listdir(repo_pkg_base))))
            this_index = self._release_index

            # Search backwards from this_index to find a prior release to diff against:
            for num in reversed(range(0, this_index)):  # reversed([0, ..., this-1]) => [this-1, ..., 0]
                if os.path.isdir(os.path.join(repo_pkg_base, str(num))):
                    last_index = num
                    break
        else:
            # Iterate over the directory contents and find the largest integer
            for filename in os.listdir(repo_pkg_base):
                if not os.path.isdir(os.path.join(repo_pkg_base, filename)):
                    continue
                try:
                    filenum = int(filename)
                except ValueError:
                    continue
                if filenum > last_index:
                    last_index = filenum

            # Find the next index >last_index that fits index_multiplier (0*n, 1*n, 2*n, 3*n, ...)
            if last_index == -1:
                # Special case: The below math would round up to 1*n
                this_index = 0
            else:
                index_multiplier = beta_index_multiplier if self._beta_release else ga_index_multiplier
                this_index = index_multiplier * (int(last_index / index_multiplier) + 1)

        return (last_index, this_index)

    def _create_universe_branch(self, scratchdir, pkgdir):
        branch = 'automated/release_{}_{}_{}'.format(
            self._pkg_name,
            self._pkg_version,
            base64.b64encode(os.urandom(4)).decode('utf-8').rstrip('='))

        # check out the repo, create a new local branch:
        ret = os.system(' && '.join([
            'cd {}'.format(scratchdir),
            'git clone --depth 1 --branch {} https://{}:{}@github.com/{} universe'.format(
                self._release_branch, self._github_user, self._github_token, self._release_universe_repo),
            'cd universe',
            'git config --local user.email jenkins@mesosphere.com',
            'git config --local user.name release_builder.py',
            'git checkout -b {}'.format(branch)]))
        if ret != 0:
            raise Exception('Failed to create local Universe git branch {}.'.format(branch))
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

        # create a user-friendly diff for use in the commit message:
        result_lines = self._compute_changes(last_dir, this_dir, last_index, this_index)
        commitmsg_path = os.path.join(scratchdir, 'commitmsg.txt')
        with open(commitmsg_path, 'w') as commitmsg_file:
            commitmsg_file.write(self._pr_title)
            commitmsg_file.writelines(result_lines)
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
            raise Exception('Failed to push git branch {} to Universe.'.format(branch))
        return (branch, commitmsg_path)

    def _compute_changes(self, last_dir, this_dir, last_index, this_index):
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

        result_lines = [
            'Changes between revisions {} => {}:\n'.format(last_index, this_index),
            '{} files added: [{}]\n'.format(len(added_files), ', '.join(added_files)),
            '{} files removed: [{}]\n'.format(len(removed_files), ', '.join(removed_files)),
            '{} files changed:\n\n'.format(len(filediffs))]
        if self._commit_desc:
            result_lines.insert(0, 'Description:\n{}\n\n'.format(self._commit_desc))

        # surround diff description with quotes to ensure formatting is preserved:
        result_lines.append('```\n')

        filediff_names = list(filediffs.keys())
        filediff_names.sort()
        for filename in filediff_names:
            result_lines.append(filediffs[filename])

        result_lines.append('```\n')

        return result_lines

    def _create_universe_pr(self, branch, commitmsg_path):
        if self._dry_run:
            log.info('[DRY RUN] Skipping creation of PR against branch {}'.format(branch))
            return None
        headers = {
            'User-Agent': 'release_builder.py',
            'Content-Type': 'application/json',
            'Authorization': 'Basic {}'.format(self._enc_github_token)}
        with open(commitmsg_path) as commitmsg_file:
            payload = {
                'title': self._pr_title,
                'head': branch,
                'base': self._release_branch,
                'body': commitmsg_file.read()}
        conn = http.client.HTTPSConnection('api.github.com')
        conn.request(
            'POST',
            '/repos/{}/pulls'.format(self._release_universe_repo),
            body=json.dumps(payload).encode('utf-8'),
            headers=headers)
        return conn.getresponse()

    def publish(self, scratchdir, pkgdir):
        branch, commitmsg_path = self._create_universe_branch(scratchdir, pkgdir)
        return self._create_universe_pr(branch, commitmsg_path)
