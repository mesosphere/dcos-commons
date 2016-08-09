#!/usr/bin/python

import base64
import json
import os
import os.path
import pprint
import re
import sys
import subprocess

try:
    from http.client import HTTPSConnection
except ImportError:
    # Python 2
    from httplib import HTTPSConnection

class GithubStatusUpdater(object):

    def __init__(self, state, context_label, message, details_url=''):
        self.__state = state
        self.__context_label = context_label
        self.__message = message
        self.__details_url = details_url


    def __get_dotgit_path(self):
        '''returns the path to the .git directory for the repo'''
        gitdir = '.git'
        if 'GIT_REPOSITORY_ROOT' in os.environ:
            startdir = os.environ['GIT_REPOSITORY_ROOT']
        else:
            startdir = os.getcwd()
        # starting at 'startdir' search up the tree for a directory named '.git':
        checkdir = os.path.join(startdir, gitdir)
        while not checkdir == '/' + gitdir:
            if os.path.isdir(checkdir):
                return checkdir
            checkdir = os.path.join(os.path.dirname(os.path.dirname(checkdir)), gitdir)
        raise Exception('Unable to find {} in any parent of {}. '.format(gitdir, startdir) +
                        'Run this command within the git repo, or provide GIT_REPOSITORY_ROOT')


    def __get_commit_sha(self):
        '''returns the sha1 of the commit being reported upon'''
        if 'GIT_COMMIT' in os.environ:
            # default envvar for referencing the git commit (provided by jenkins)
            return os.environ['GIT_COMMIT']
        if 'GIT_COMMIT_ENV_NAME' in os.environ:
            # grab commit from specified envvar
            checkval = os.environ[os.environ['GIT_COMMIT_ENV_NAME']]
            if not checkval:
                raise Exception('Unable to retrieve git commit id from envvar named "{}". Env is: {}'.format(
                    os.environ['GIT_COMMIT_ENV_NAME'], os.environ))
            return checkval
        # fall back to using .git (which isn't present in teamcity)
        dotgit_path = self.__get_dotgit_path()
        ret = subprocess.Popen(['git', '--git-dir={}'.format(dotgit_path), 'rev-parse', 'HEAD'],
                               stdout=subprocess.PIPE)
        result = ret.stdout.readline().decode('utf-8').strip()
        if not result:
            raise Exception('Failed to retrieve current revision from git: {}'.format(dotgit_path))
        return result


    def __get_repo_path(self):
        '''returns the repository path, in the form "mesosphere/some-repo"'''
        if 'GITHUB_REPO_PATH' in os.environ:
            return os.environ['GITHUB_REPO_PATH']
        dotgit_path = self.__get_dotgit_path()
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


    def __get_details_link_url(self):
        '''returns the url to be included as the details link in the status'''
        if self.__details_url: # custom URL via parameter
            return self.__details_url
        if 'GITHUB_COMMIT_STATUS_URL' in os.environ: # custom URL via env
            return os.environ['GITHUB_COMMIT_STATUS_URL']
        if 'BUILD_URL' in os.environ: # provided by jenkins
            return os.environ['BUILD_URL'] + 'console'
        raise Exception('Failed to determine URL for details link. ' +
                        'Provide either GITHUB_COMMIT_STATUS_URL or BUILD_URL.')


    def __get_auth_token(self):
        github_token = os.environ.get('GITHUB_TOKEN_REPO_STATUS', '')
        if not github_token:
            github_token = os.environ.get('GITHUB_TOKEN', '')
        if not github_token:
            raise Exception('GITHUB_TOKEN or GITHUB_TOKEN_REPO_STATUS is required: Auth token to use with Github')
        encoded_tok = base64.encodestring(github_token.encode('utf-8'))
        return encoded_tok.decode('utf-8').rstrip('\n')


    def request_info(self, auth_token):
        '''returns everything needed for the HTTP request, except the auth token'''
        return {
            'method': 'POST',
            'path': '/repos/{}/commits/{}/statuses'.format(
                self.__get_repo_path(),
                self.__get_commit_sha()),
            'headers': {
                'User-Agent': 'github_update.py',
                'Content-Type': 'application/json',
                'Authorization': 'Basic {}'.format(auth_token)},
            'payload': {
                'state': self.__state,
                'context': self.__context_label,
                'description': self.__message,
                'target_url': self.__get_details_link_url()
            }
        }


    def update(self):
        '''sends an update to github. returns the resulting HTTPResponse.'''
        request = self.request_info(self.__get_auth_token())
        conn = HTTPSConnection('api.github.com')
        #conn.set_debuglevel(999)
        conn.request(
            request['method'],
            request['path'],
            body = json.dumps(request['payload']).encode('utf-8'),
            headers = request['headers'])
        return conn.getresponse()


def print_help(argv):
    print('Syntax: {} <state: pending|success|error|failure> <context_label> <status message>'.format(argv[0]))


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
    if 'JENKINS_HOME' in os.environ:
        # in CI: actually update status (otherwise just print below)
        updater = GithubStatusUpdater(state, context_label, message)
        response = updater.update()
        request_info = updater.request_info('<base64_token>')
        if response.status < 200 or response.status >= 300:
            print('Got {} response to update request:'.format(response.status))
            print('Request:')
            pprint.pprint(request_info)
            print('Response:')
            pprint.pprint(response.read())
            return 1
        print('Updated GitHub PR with status: {}'.format(request_info['path']))
    # print status to log
    print('[STATUS] {} {}: {}'.format(context_label, state, message))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
