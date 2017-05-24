#!/usr/bin/env python3
"""
Module to contain information about running or to-be-run framework tests.
Specifically:
 What cluster is being used.
 Whether the framework has been built.
 Whether the test is currently running (asynchronously).
 A proxy to access the output of the tests.
"""

import collections
import json
import logging
import os
import random
import time

logger = logging.getLogger(__name__)
#logging.basicConfig(level=logging.DEBUG, format="%(asctime)s %(module)s %(message)s")

# holds info objects -- TODO: need ordering
_framework_infos = []

_repo_root=None
def init_repo_root(repo_root):
    global _repo_root
    _repo_root=repo_root


def add_framework(framework_name, repo_root=None, dcos_version=None):
    if not repo_root:
        repo_root=_repo_root
    logger.debug("add_framework(%s, repo_root=%s)", framework_name, repo_root)
    if get_framework(framework_name):
        raise Exception("Tried to add framework %s when it already exists")
    fwobj = FrameworkTestInfo(framework_name, repo_root)
    if not fwobj.supports_version(dcos_version):
        logger.info("Skipping framework %s, which does not support dcos version %s",
                framework_name, dcos_version)
        return None
    _framework_infos.append(fwobj)
    return fwobj

def get_framework(framework_name):
    for stored_framework in get_frameworks():
        if framework_name == stored_framework.name:
            return stored_framework
    return None

def get_frameworks():
    return _framework_infos

def get_framework_names():
    return [framework.name for framework in get_frameworks()]

def have_framework(framework_name):
    if get_framework(framework_name):
        return True
    return False

def autodiscover_frameworks(repo_root=None, dcos_version=None):
    if not repo_root:
        repo_root=_repo_root
    if not repo_root:
        raise Exception("No repo_root provided directly or via init_repo_root()")
    if _framework_infos:
        logger.warning( "discover_frameworks() called after frameworks already identified.  Cowardly ignoring.")
        return
    frameworks_dir = os.path.join(repo_root, 'frameworks')
    frameworks = os.listdir(frameworks_dir)
    for framework in frameworks:
        add_framework(framework, repo_root, dcos_version=dcos_version)

def shuffle_order():
    random.shuffle(_framework_infos)

def running_frameworks():
    return [framework for framework in get_frameworks() if framework.running]


class FrameworkTestInfo(object):
    def __init__(self, framework_name, repo_root):
        self.name = framework_name
        self.built = False
        self.running = False
        self.cluster = None
        self.test_success = None # set to True or False later
        self.stub_universe_urls = [] # url(s) where cluster can download framework
        self.output_file = None # in background
        self.popen = None
        self.dir = os.path.join(repo_root, 'frameworks', self.name)
        self.buildscript = os.path.join(self.dir, 'build.sh')
        # TODO figure out what this trailing slash is for and eliminate
        self.testdir = os.path.join(self.dir, 'tests') + "/"
        self.actions = collections.OrderedDict() # succeeded and failed steps land here
        self._determine_minimum_dcos_version()

    def __repr__(self):
        # <classname frameworkname>
        return "<%s %s>" % (self.__class__.__name__, self.name)

    def _determine_minimum_dcos_version(self):
        universe_package_json = os.path.join(self.dir, 'universe', 'package.json')
        with open(universe_package_json) as f:
            text = f.read()
            package_info = json.loads(text)
            # fully possible for this to return None, which is correct
            min_dcos_version = package_info.get("minDcosReleaseVersion")
            self.min_dcos_version = min_dcos_version

    def supports_version(self, available_version):
        """ Given an version number for a running DCOS cluster, does this
        framework think it can run on the passed-in dcos version?"""
        if not self.min_dcos_version or not available_version:
            return True
        # render versions as tuples
        avail_tuple = available_version.split('.')
        avail_tuple = tuple(map(int, avail_tuple))
        minversion_tuple = self.min_dcos_version.split('.')
        minversion_tuple = tuple(map(int, minversion_tuple))
        return avail_tuple >= minversion_tuple

    def start_action(self, name):
        self.actions[name] = {'start': time.time()}

    def finish_action_ok(self, name, **args):
        action = self.actions[name]
        action['ok'] = True
        action['finish'] = time.time()
        self._complete_action(action, **args)

    def finish_action_fail(self, name, **args):
        action = self.actions[name]
        action['ok'] = False
        action['finish'] = time.time()
        self._complete_action(action, **args)

    def _complete_action(self, action, **args):
        action['stdout'] = args.get('stdout')
        action['stderr'] = args.get('stderr')
        # failures
        action['error_message'] = args.get('error_message')
        action['error_output'] = args.get('error_output')

if __name__ == "__main__":
    import unittest
    rel_root_path = os.path.dirname(os.path.dirname(__file__))
    abs_repo_root = os.path.abspath(rel_root_path)

    class tests(unittest.TestCase):
        def setUp(self):
            global _framework_infos
            _framework_infos = []
            global _repo_root
            _repo_root=None

        def test_fail_without_repo_init(self):
            self.assertRaises(Exception, autodiscover_frameworks)

        def test_discover_works_after_init(self):
            init_repo_root(abs_repo_root)
            autodiscover_frameworks()
            self.assertEqual(len(get_frameworks()), 9)
            self.assertIn('helloworld', get_framework_names())


        def test_add_with_root(self):
            add_framework('proxylite', repo_root=abs_repo_root)
            self.assertEqual(get_framework_names(), ['proxylite'])

        def test_add_without_root(self):
            init_repo_root(abs_repo_root)
            self.assertFalse(have_framework('proxylite'))
            add_framework('proxylite')
            self.assertTrue(have_framework('proxylite'))
            self.assertEqual(get_framework_names(), ['proxylite'])

        def test_add_dupe_framework(self):
            init_repo_root(abs_repo_root)
            add_framework('proxylite')
            self.assertRaises(Exception, add_framework, 'proxylite')

        def test_running_frameworks(self):
            init_repo_root(abs_repo_root)
            autodiscover_frameworks()
            self.assertEqual(len(running_frameworks()), 0)
            hello_fw = get_framework('helloworld')
            hello_fw.running = True
            self.assertEqual(len(running_frameworks()), 1)
            hello_fw.running = False
            self.assertEqual(len(running_frameworks()), 0)

    unittest.main()
