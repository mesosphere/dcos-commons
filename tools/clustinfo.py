#!/usr/bin/env python3
"""
Module to contain information about running clusters to use for testing.
Specifically:
 Access URL
 auth token
 node count
 whether they were auto-created during test invocation
"""

import logging
import os
import time

import launch_ccm_cluster

logger = logging.getLogger(__name__)

# holds info objects
_clusters = []

def start_cluster(launch_config=None, reporting_name=None):
    if not reporting_name:
        reporting_name="only_cluster"

    _launch_recorder.start(reporting_name)
    try:
        cluster = _launch_cluster(launch_config)
        _launch_recorder.finish_ok(reporting_name, cluster)
    except:
        _launch_recorder.finish_fail(reporting_name)
        raise
    _clusters.append(cluster)
    logger.info("Started cluster: %s", cluster.cluster_id)
    return cluster

def get_launch_attempts():
    return _launch_recorder.get_list()

def add_running_cluster(url, auth_token):
    cluster = ClusterInfo(url, auth_token, external=True)
    _clusters.append(cluster)
    return cluster

def get_cluster_by_url(cluster_url):
    matches = [cluster for cluster in _clusters if cluster.url == cluster_url]
    assert len(matches) in (0, 1)
    if matches:
        return matches[0]
    return None

def running_count():
    return len(_clusters)

def get_idle_cluster():
    for cluster in _clusters:
        if not cluster.in_use():
            return cluster
    return None

def stop_cluster(cluster):
    github_label = launch_ccm_cluster.determine_github_label()
    ccm_token = os.environ['CCM_AUTH_TOKEN']
    launcher = launch_ccm_cluster.CCMLauncher(ccm_token, github_label)

    id_as_str = str(cluster.cluster_id) # launch_ccm_cluster is truly sloppy
    config = launch_ccm_cluster.StopConfig(id_as_str)
    launcher.trigger_stop(config)

def shutdown_clusters(shutdown_external=False):
    for cluster in list(_clusters):
        if cluster.external and not shutdown_external:
            continue
        stop_cluster(cluster)
        _clusters.remove(cluster)

def _launch_cluster(launch_config=None):
    ccm_token = os.environ['CCM_AUTH_TOKEN']
    if not launch_config:
        launch_config = launch_ccm_cluster.StartConfig(private_agents=6)
    cluster_info = launch_ccm_cluster.start_cluster(ccm_token, launch_config)
    cluster = ClusterInfo(cluster_info["url"], cluster_info["auth_token"],
            cluster_id=cluster_info["id"])
    return cluster


class ClusterInfo(object):
    def __init__(self, url, auth_token, cluster_id=None, external=False):
        self.url = url
        self.auth_token = auth_token
        self.cluster_id = cluster_id
        self.external = external # launched outside local automation
        self._frameworks_using = set()
        # self.node_count etc

    def claim(self, framework):
        self._frameworks_using.add(framework)

    def unclaim(self, framework):
        self._frameworks_using.remove(framework)

    def in_use(self):
        return self._frameworks_using

    def is_running(self):
        return True


class _LaunchRecorder(object):
    class Entry(object):
        def __init__(self, name):
            self.name = name
            self.launch_succeeded = None
            self.clust_info = None
            self.start_time = time.time()
            self.end_time = None

    def __init__(self):
        self.launch_list = []

    def get_list(self):
        return self.launch_list

    def get_ent(self, name):
        for ent in self.launch_list:
            if ent.name == name:
                return ent
        return None

    def start(self, name):
        if self.get_ent(name):
            raise Exception("No duplicate launch names.")
        entry = self.Entry(name)
        self.launch_list.append(entry)

    def finish_ok(self, name, cluster):
        ent = self.get_ent(name)
        if not ent:
            raise Exception("finish_ok() called on unknown name=%s" % name)
        ent.end_time = time.time()
        ent.launch_succeeded = True
        ent.cluster = cluster

    def finish_fail(self, name):
        ent = self.get_ent(name)
        if not ent:
            raise Exception("finish_fail() called on unknown name=%s" % name)
        ent.end_time = time.time()
        ent.launch_succeeded = False

_launch_recorder = _LaunchRecorder()


## tests

def _mock_launch_cluster(config=None):
    cluster = ClusterInfo("Im a url", "Im an auth token",
            12345)
    return cluster
