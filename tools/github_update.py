#!/usr/bin/python

# Env Vars
#   GITHUB_TOKEN: github auth token
#   GIT_COMMIT | ghprbActualCommit | sha1 (optional)
#   GITHUB_DISABLE (optional): if non-empty, this script performs no action
#   GITHUB_REPOSITORY_ROOT (optional): directory in which to look for .git (unused if GIT_COMMIT is set)
#   GITHUB_REPO_PATH (optional): path to repo to update (e.g. mesosphere/spark)

import base64
import datetime
import json
import logging
import os
import os.path
import pprint
import re
import sys
import subprocess
import tempfile
import time

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")

try:
    from http.client import HTTPSConnection
except ImportError:
    # Python 2
    from httplib import HTTPSConnection

# reserved contexts which may not be modified by this script
BLACKLISTED_CONTEXT_LABELS = ['velocity'] # not set by us, do not update

# the pending/incomplete state
PENDING_STATE = 'pending'

# list of all valid states
VALID_STATES = [PENDING_STATE, 'success', 'error', 'failure']


class RepoInfo(object):

    def _get_dotgit_path(self):
        '''returns the path to the .git directory for the repo'''
        gitdir = '.git'
        startdir = os.environ.get('GIT_REPOSITORY_ROOT')
        if not startdir:
            startdir = os.getcwd()
        # starting at 'startdir' search up the tree for a directory named '.git':
        checkdir = os.path.join(startdir, gitdir)
        while checkdir != '/' + gitdir:
            if os.path.isdir(checkdir):
                return checkdir
            checkdir = os.path.join(os.path.dirname(os.path.dirname(checkdir)), gitdir)
        raise Exception('Unable to find {} in any parent of {}. '.format(gitdir, startdir) +
                        'Run this command within the git repo, or provide GIT_REPOSITORY_ROOT')


    def commit_sha(self):
        '''returns the sha1 of the commit being reported upon'''
        # 1. try 'ghprbActualCommit', 'GIT_COMMIT', and 'sha1' envvars:
        commit_sha = os.environ.get('ghprbActualCommit') or os.environ.get('GIT_COMMIT') or os.environ.get('sha1')
        if not commit_sha and 'GIT_COMMIT_ENV_NAME' in os.environ:
            # 2. grab the commit from the specified custom envvar
            commit_sha = os.environ.get(os.environ['GIT_COMMIT_ENV_NAME'])
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


    def repo_orgname(self):
        '''returns the repository path, in the form "mesosphere/some-repo"'''
        repo_path = os.environ.get('GITHUB_REPO_PATH')
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


    def github_auth_token(self):
        github_token = os.environ.get('GITHUB_TOKEN_REPO_STATUS') or os.environ.get('GITHUB_TOKEN')
        if not github_token:
            raise Exception(
                'Failed to determine auth token to use with GitHub. ' +
                'Provide either GITHUB_TOKEN or GITHUB_TOKEN_REPO_STATUS in env.')
        return github_token


class GithubAPI(object):

    def __init__(self, repo_orgname, commit_sha, github_token, debug_requests=False):
        self._repo_orgname = repo_orgname
        self._commit_sha = commit_sha
        self._github_token = github_token
        self._debug_requests = debug_requests


    def _send_request(self, method, path, json_payload=None):
        '''sends the provided request to api.github.com and returns the response, or None if the request failed'''
        if json_payload:
            body = json.dumps(json_payload).encode('utf-8')
        else:
            body = None

        encoded_tok = base64.encodestring(self._github_token.encode('utf-8')).decode('utf-8').rstrip('\n')
        headers = {
            'User-Agent': 'github_update.py',
            'Content-Type': 'application/json',
            'Authorization': 'Basic {}'.format(encoded_tok)}

        conn = HTTPSConnection('api.github.com')
        if self._debug_requests:
            conn.set_debuglevel(999)
        conn.request(method, path, body=body, headers=headers)
        response = conn.getresponse()
        if response.status < 200 or response.status >= 300:
            # log failure, but don't abort the build
            logger.error('Got {} response with content:'.format(response.status))
            logger.error(pprint.pformat(response.read().decode('utf-8')))
            return None
        return response


    def get_commit_statuses(self):
        response = self._send_request(
            'GET',
            '/repos/{}/commits/{}/statuses'.format(self._repo_orgname, self._commit_sha))
        if not response:
            return []
        return json.loads(response.read().decode('utf-8'))


    def set_commit_status(self, context_label, state, message, details_url):
        payload = {
            'context': context_label,
            'state': state
        }
        if message:
            payload['description'] = message
        if details_url:
            payload['target_url'] = details_url
        self._send_request(
            'POST',
            '/repos/{}/commits/{}/statuses'.format(self._repo_orgname, self._commit_sha),
            payload)


class GithubStatusUpdater(object):

    def __init__(self, default_context_label=''):
        info = RepoInfo()
        self._api = GithubAPI(info.repo_orgname(), info.commit_sha(), info.github_auth_token())
        self._default_context_label = default_context_label


    def list_contexts(self):
        '''returns a set of context labels of all statuses in a given commit'''
        return set([status['context'] for status in self._api.get_commit_statuses()])


    def update(self, state, message='', details_url='', context_label=''):
        '''sends a commit status update to github.
        returns True on success or False otherwise.
        state should be one of the values in 'VALID_STATES'.'''
        if not context_label:
            context_label = self._default_context_label
        if not context_label:
            raise Exception('Either update() call must provide a context, ' +
                            'or a default context must have been set')

        start_time_path = os.path.join(
            tempfile.gettempdir(),
            'github_update-{}'.format(re.sub('[^0-9a-zA-Z]+', '_', context_label)))
        if state == PENDING_STATE:
            # store start time to tmp file:
            start_time_file = open(start_time_path, 'w')
            start_time_file.write('{}\n'.format(str(time.time())))
            start_time_file.flush()
            start_time_file.close()
        elif os.path.isfile(start_time_path):
            # when available, get start time from tmp file and include duration in message:
            try:
                start_time = float(open(start_time_path).read().strip())
                # omit fractional seconds from duration:
                message = '[{}] {}'.format(datetime.timedelta(seconds=round(time.time() - start_time)), message)
                os.remove(start_time_path)
            except:
                message = '[?] {}'.format(message)

        self._api.set_commit_status(context_label, state, message, details_url)


def _get_details_link_url():
    '''returns the url to be included as the details link in the status'''
    details_url = os.environ.get('GITHUB_COMMIT_STATUS_URL') # custom URL via env
    if not details_url:
        details_url = os.environ.get('BUILD_URL') # provided by jenkins
        if details_url:
            details_url += 'console'
    return details_url


def _should_access_github():
    if 'WORKSPACE' not in os.environ:
        # not running in CI. skip actually sending anything to GitHub
        return False
    if os.environ.get('GITHUB_DISABLE', ''):
        # environment has notifications disabled. skip actually sending anything to GitHub
        return False
    if not (os.environ.get('GITHUB_COMMIT_STATUS_URL') or os.environ.get('BUILD_URL')):
        # CI job was not triggered by a PR
        return False
    return True


def print_help(argv):
    logger.info('Syntax:')
    logger.info('- Update: {} <state: {}> <context_label> [a status message ...]'.format(
        argv[0], '|'.join(VALID_STATES)))
    logger.info('- Reset pending: {} reset [a replacement message ...]'.format(argv[0]))


def reset_states(updater, message):
    if not _should_access_github():
        # reset disabled due to local build, exit silently
        return 0

    contexts = sorted([context for context in updater.list_contexts() if context not in BLACKLISTED_CONTEXT_LABELS])
    if not contexts:
        # nothing to reset, exit silently
        return 0

    logger.info('[GH-STATUS] Resetting {} statuses: {}'.format(len(contexts), ', '.join(contexts)))
    for context_label in contexts:
        updater.update(PENDING_STATE, message=message, context_label=context_label)
    return 0


def set_state(updater, state, context_label, message):
    if context_label in BLACKLISTED_CONTEXT_LABELS:
        logger.error('Requested context label is on the blacklisted list: {}'.format(BLACKLISTED_CONTEXT_LABELS))
        return 1

    details_url = _get_details_link_url()
    if details_url:
        logmsg = '{} {}: {} ({})'.format(context_label, state, message, details_url)
    else:
        logmsg = '{} {}: {}'.format(context_label, state, message)

    if _should_access_github():
        logger.info('[GH-STATUS] {}'.format(logmsg))
        updater.update(state, message=message, context_label=context_label, details_url=details_url)
    else:
        logger.info('[STATUS] {}'.format(logmsg))
    return 0


def main(argv):
    if len(argv) < 2:
        print_help(argv)
        return 1

    updater = GithubStatusUpdater()
    command = argv[1]
    if command == 'reset':
        return reset_states(updater, ' '.join(argv[2:]))
    elif command in VALID_STATES:
        if len(argv) < 4:
            print_help(argv)
            return 1
        return set_state(updater, command, argv[2], ' '.join(argv[3:]))
    else:
        print_help(argv)
        return 1


if __name__ == '__main__':
    sys.exit(main(sys.argv))
