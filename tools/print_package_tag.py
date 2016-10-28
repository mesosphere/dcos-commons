#!/usr/bin/python

# Retrieves package version from a cluster using the CLI (must be configured/logged in),
# then determines what SHA that package has in the provided repo (must be locally checked out)
#
# On success: Prints package version string and zero is returned
# On failure: non-zero is returned

import json
import logging
import os.path
import subprocess
import sys

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


class PackageVersion(object):

    def __init__(self, package_name):
        self._package_name = package_name


    def get_version(self):
        response_raw = self._get_cmd_stdout('dcos package describe {}'.format(self._package_name))
        try:
            return json.loads(response_raw)['version']
        except:
            logger.error('Failed to parse version from output of command "{}": {}'.format(cmd, response_raw))
            raise


    def get_version_sha_for_path(self, repo_path):
        version_tag = self.get_version()
        try:
            # ensure the tag is visible in the repo copy:
            repo_dotgit_path = os.path.join(repo_path, '.git')
            self._get_cmd_stdout('git --git-dir={} fetch origin --tags'.format(repo_dotgit_path))
            # get the rev for the tag. use % instead of .format() to preserve a literal '{}':
            return self._get_cmd_stdout('git --git-dir=%s rev-parse %s^{}' % (repo_dotgit_path, version_tag))
        except:
            logger.error('Failed to retrieve SHA1 from git for tag "{}"'.format(version_tag))
            raise


    def get_version_sha_for_url(self, repo_url):
        version_tag = self.get_version()
        try:
            # get the rev for the remote tag. use % instead of .format() to preserve a literal '{}':
            rev = self._get_cmd_stdout('git ls-remote --tags %s refs/tags/%s^{}' % (repo_url, version_tag))
            if len(rev) == 0:
                # no tag with '^{}' suffix was found. retry without the suffix:
                rev = self._get_cmd_stdout('git ls-remote --tags {} refs/tags/{}'.format(repo_url, version_tag))
            # output format: '<tag>           <refname>'
            return rev.split()[0]
        except:
            logger.error('Failed to retrieve SHA1 from git for tag "{}"'.format(version_tag))
            raise


    def _get_cmd_stdout(self, cmd):
        try:
            logger.info("CMD: {}".format(cmd))
            output = subprocess.check_output(cmd.split(' ')).decode('utf-8').strip()
            logger.info("Output ({}b):\n{}".format(len(output), output))
            return output
        except:
            logger.error('Failed to run command: "{}"'.format(cmd))
            raise


def main(argv):
    if len(argv) != 2 and len(argv) != 3:
        logger.error('Syntax: {} <package> [/local/repo/path or git@host.com:remote/repo]'.format(argv[0]))
        logger.error('Received arguments {}'.format(str(argv)))
        return 1
    if len(argv) == 2:
        print(PackageVersion(argv[1]).get_version())
    else:
        if os.path.isdir(argv[2]):
            print(PackageVersion(argv[1]).get_version_sha_for_path(argv[2]))
        else:
            print(PackageVersion(argv[1]).get_version_sha_for_url(argv[2]))

    return 0

if __name__ == '__main__':
    sys.exit(main(sys.argv))
