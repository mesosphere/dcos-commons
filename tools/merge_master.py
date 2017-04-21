#!/usr/bin/env python3
"""In a continuous integration environment, merge the master branch into the
current branch before build & test"""

import argparse
import logging
import os
import subprocess
import sys

import github_update


logger = logging.getLogger(__name__)
if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")


def parse_args(args):
    parser = argparse.ArgumentParser(
        description="merge a branch to the current branch for continuous integration goals")
    parser.add_argument("branch",
            nargs="?",
            help="Branch to merge",
            default="master")
    return parser.parse_args(args)


def git_merge_current(branch):
    """ Merge a (remote) branch into the current branch without a commit """
    if not 'WORKSPACE' in os.environ:
        raise Exception("cannot find env var WORKSPACE. merge_master.py is only intended to be used in jenkins jobs.")
    logging.info("Attempting to merge changes from %s", branch)
    # The values here for user email & name shouldn't matter because we try to
    # never commit, but git is fussy.
    logging.info("Creating fake user")
    set_email = ['git', 'config',
                 'user.email', 'pullrequestbot@mesospherebot.com']
    logging.info(" ".join(set_email))
    subprocess.check_call(set_email)
    set_name = ['git', 'config',
                 'user.name', 'Infinity-tools-fake-user']
    logging.info(" ".join(set_name))
    subprocess.check_call(set_name)
    # This particular merge incantation is intended to just fail on merge
    # conflict.
    merge_remote = ['git', 'pull', 'origin', branch, '--no-commit', '--ff']
    logging.info(" ".join(merge_remote))
    subprocess.check_call(merge_remote)


def merge_with_updates(branch):
    """ Merge a (remote) branch into the current branch without a commit,
    and update the current pull request with status """
    updater = github_update.GithubStatusUpdater('branchmerge')
    try:
        git_merge_current(branch)
        updater.update('success', 'Merge from {} branch done'.format(branch))
    except:
        updater.update('failure', 'Merge from {} branch failed'.format(branch))
        raise


def main(args=sys.argv[1:]):
    branch = parse_args(args).branch
    merge_with_updates(branch)
    return True


if __name__ == "__main__":
    if not main():
        sys.exit(1)
