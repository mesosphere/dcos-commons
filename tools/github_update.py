#!/usr/bin/python

# Env Vars
#   GITHUB_TOKEN: github auth token
#   GIT_COMMIT | ghprbActualCommit | sha1 (optional)
#   GITHUB_DISABLE (optional): if non-empty, this script performs no action
#   GITHUB_REPOSITORY_ROOT (optional): directory in which to look for .git (unused if GIT_COMMIT is set)
#   GITHUB_REPO_PATH (optional): path to repo to update (e.g. mesosphere/spark)

import base64
import json
import logging
import os
import os.path
import pprint
import re
import sys
import subprocess

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")

try:
    from http.client import HTTPSConnection
except ImportError:
    # Python 2
    from httplib import HTTPSConnection

class GithubStatusUpdater(object):

    def __init__(self, context_label):
        self._context_label = context_label


    def _get_dotgit_path(self):
        '''returns the path to the .git directory for the repo'''
        gitdir = '.git'
        startdir = os.environ.get('GIT_REPOSITORY_ROOT', '')
        if not startdir:
            startdir = os.getcwd()
        # starting at 'startdir' search up the tree for a directory named '.git':
        checkdir = os.path.join(startdir, gitdir)
        while not checkdir == '/' + gitdir:
            if os.path.isdir(checkdir):
                return checkdir
            checkdir = os.path.join(os.path.dirname(os.path.dirname(checkdir)), gitdir)
        raise Exception('Unable to find {} in any parent of {}. '.format(gitdir, startdir) +
                        'Run this command within the git repo, or provide GIT_REPOSITORY_ROOT')


    def _get_commit_sha(self):
        '''returns the sha1 of the commit being reported upon'''
        # 1. try 'ghprbActualCommit', 'GIT_COMMIT', and 'sha1' envvars:
        commit_sha = os.environ.get('ghprbActualCommit', '')
        if not commit_sha:
            commit_sha = os.environ.get('GIT_COMMIT', '')
        if not commit_sha:
            commit_sha = os.environ.get('sha1', '')
        if not commit_sha and 'GIT_COMMIT_ENV_NAME' in os.environ:
            # 2. grab the commit from the specified custom envvar
            commit_sha = os.environ.get(os.environ['GIT_COMMIT_ENV_NAME'], '')
            if not commit_sha:
                raise Exception('Unable to retrieve git commit id from envvar named "{}". Env is: {}'.format(
                    os.environ['GIT_COMMIT_ENV_NAME'], os.environ))
        if not commit_sha:
            # 3. fall back to using current commit according to .git/ (note: not present in teamcity)
            dotgit_path = self._get_dotgit_path()
            ret = subprocess.Popen(['git', '--git-dir={}'.format(dotgit_path), 'rev-parse', 'HEAD'],
                                   stdout=subprocess.PIPE)
            commit_sha = ret.stdout.readline().decode('utf-8').strip()
        if not commit_sha:
            raise Exception('Failed to retrieve current revision from git: {}'.format(dotgit_path))
        return commit_sha


    def _get_repo_path(self):
        '''returns the repository path, in the form "mesosphere/some-repo"'''
        repo_path = os.environ.get('GITHUB_REPO_PATH', '')
        if repo_path:
            return repo_path
        dotgit_path = self._get_dotgit_path()
        ret = subprocess.Popen(['git', '--git-dir={}'.format(dotgit_path), 'config', 'remote.origin.url'],
                               stdout=subprocess.PIPE)
        full_url = ret.stdout.readline().decode('utf-8').strip()
        # expected url formats:
        # 'https://github.com/mesosphere/foo'
        # 'git@github.com:/mesosphere/foo.git'
        # 'git@github.com:/mesosphere/foo'
        # 'git@github.com/mesosphere/foo.git
        # 'git@github.com/mesosphere/foo'
        # all should result in 'mesosphere/foo'
        re_match = re.search('([a-zA-Z0-9-]+/[a-zA-Z0-9-]+)(\\.git)?$', full_url)
        if not re_match:
            raise Exception('Failed to get remote url from git path {}: no match in {}'.format(
                dotgit_path, full_url))
        return re_match.group(1)


    def _get_details_link_url(self, details_url):
        '''returns the url to be included as the details link in the status'''
        if not details_url:
            details_url = os.environ.get('GITHUB_COMMIT_STATUS_URL', '') # custom URL via env
        if not details_url:
            details_url = os.environ.get('BUILD_URL', '') # provided by jenkins
            if details_url:
                details_url += 'console'
        if not details_url:
            raise Exception(
                'Failed to determine URL for details link. ' +
                'Provide either GITHUB_COMMIT_STATUS_URL or BUILD_URL in env.')
        return details_url


    def _get_auth_token(self):
        github_token = os.environ.get('GITHUB_TOKEN_REPO_STATUS', '')
        if not github_token:
            github_token = os.environ.get('GITHUB_TOKEN', '')
        if not github_token:
            raise Exception(
                'Failed to determine auth token to use with GitHub. ' +
                'Provide either GITHUB_TOKEN or GITHUB_TOKEN_REPO_STATUS in env.')
        encoded_tok = base64.encodestring(github_token.encode('utf-8'))
        return encoded_tok.decode('utf-8').rstrip('\n')


    def _build_request(self, state, message, details_url = ''):
        '''returns everything needed for the HTTP request, except the auth token'''
        return {
            'method': 'POST',
            'path': '/repos/{}/commits/{}/statuses'.format(
                self._get_repo_path(),
                self._get_commit_sha()),
            'headers': {
                'User-Agent': 'github_update.py',
                'Content-Type': 'application/json',
                'Authorization': 'Basic HIDDENTOKEN'}, # replaced within update_query
            'payload': {
                'context': self._context_label,
                'state': state,
                'description': message,
                'target_url': self._get_details_link_url(details_url)
            }
        }


    def _send_request(self, request, debug = False):
        '''sends the provided request which was created by _build_request()'''
        request_headers_with_auth = request['headers'].copy()
        request_headers_with_auth['Authorization'] = 'Basic {}'.format(self._get_auth_token())
        conn = HTTPSConnection('api.github.com')
        if debug:
            conn.set_debuglevel(999)
        conn.request(
            request['method'],
            request['path'],
            body = json.dumps(request['payload']).encode('utf-8'),
            headers = request_headers_with_auth)
        return conn.getresponse()


    def update(self, state, message, details_url = ''):
        '''sends an update to github.
        returns True on success or False otherwise.
        state should be one of 'pending', 'success', 'error', or 'failure'.'''
        logger.info('[STATUS] {} {}: {}'.format(self._context_label, state, message))
        if details_url:
            logger.info('[STATUS] URL: {}'.format(details_url))

        if not 'WORKSPACE' in os.environ:
            # not running in CI. skip actually sending anything to GitHub
            return True
        if os.environ.get('GITHUB_DISABLE', ''):
            # environment has notifications disabled. skip actually sending anything to GitHub
            return True
        if not (os.environ.get('GITHUB_COMMIT_STATUS_URL') or os.environ.get('BUILD_URL')):
            # CI job did not come from GITHUB
            return True


        request = self._build_request(state, message, details_url)
        response = self._send_request(request)
        if response.status < 200 or response.status >= 300:
            # log failure, but don't abort the build
            logger.error('Got {} response to update request:'.format(response.status))
            logger.error('Request:')
            logger.error(pprint.pformat(request))
            logger.error('Response:')
            logger.error(pprint.pformat(response.read()))
            return
        logger.info('Updated GitHub PR with status: {}'.format(request['path']))


def print_help(argv):
    logger.info('Syntax: {} <state: pending|success|error|failure> <context_label> <status message>'.format(argv[0]))


def main(argv):
    if len(argv) < 4:
        print_help(argv)
        return 1
    state = argv[1]
    if state != 'pending' \
            and state != 'success' \
            and state != 'error' \
            and state != 'failure':
        print_help(argv)
        return 1
    context_label = argv[2]
    message = ' '.join(argv[3:])
    GithubStatusUpdater(context_label).update(state, message)


if __name__ == '__main__':
    sys.exit(main(sys.argv))
