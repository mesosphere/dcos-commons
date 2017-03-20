#!/usr/bin/env python3
"""
Module to contain information about running or to-be-run framework tests.
Specifically:
 What cluster is being used.
 Whether the framework has been built.
 Whether the test is currently running (asynchronously).
 A proxy to access the output of the tests.
"""

import os
import logging

logger = logging.getLogger(__name__)
#logging.basicConfig(level=logging.DEBUG, format="%(asctime)s %(module)s %(message)s")

# holds info objects
_framework_infos = {}

_repo_root=None
def init_repo_root(repo_root):
    global _repo_root
    _repo_root=repo_root

def add_framework(framework_name, repo_root=None):
    if not repo_root:
        repo_root=_repo_root
    logger.debug("add_framework(%s, repo_root=%s)", framework_name, repo_root)
    if framework_name in _framework_infos:
        raise Exceptoin("Tried to add framework %s when it already exists")
    fwobj = FrameworkTestInfo(framework_name, repo_root)
    _framework_infos[framework_name] = fwobj
    return fwobj

def get_framework(framework_name):
    return _framework_infos[framework_name]

def get_frameworks():
    return _framework_infos.values()

def get_framework_names():
    return _framework_infos.keys()

def have_framework(framework_name):
    return framework_name in _framework_infos

def autodiscover_frameworks(repo_root=None):
    if not repo_root:
        repo_root=_repo_root
    if _framework_infos:
        logger.warning( "discover_frameworks() called after frameworks already identified.  Cowardly ignoring.")
        return
    frameworks_dir = os.path.join(repo_root, 'frameworks')
    frameworks = os.listdir(frameworks_dir)
    for framework in frameworks:
        add_framework(framework, repo_root)

def running_frameworks():
    return [framework for framework in _framework_infos.values() if framework.running]

class FrameworkTestInfo(object):
    def __init__(self, framework_name, repo_root):
        self.name = framework_name
        self.built = False
        self.running = False
        self.cluster = None
        self.test_success = None # set to True or False later
        self.stub_universe_url = None # url where cluster can download framework
        self.output_file = None # in background
        self.popen = None
        self.dir = os.path.join(repo_root, 'frameworks', self.name)
        self.buildscript = os.path.join(self.dir, 'build.sh')
        # TODO figure out what this trailing slash is for and eliminate
        self.testdir = os.path.join(self.dir, 'tests') + "/"
