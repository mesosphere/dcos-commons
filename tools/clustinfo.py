#!/usr/bin/env python3
"""
Module to contain information about running clusters to use for testing.
Specifically:
 Access URL
 auth token
 node count
 whether they were auto-created during test invocation
"""

import os

import launch_ccm_cluster

# holds info objects
_clusters = []

def start_cluster(launch_config=None):
    cluster = _launch_cluster(launch_config)
    _clusters.append(cluster)
    return cluster

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
    github_label = launch_ccm_cluster.determine_github_label()
    ccm_token = os.environ['CCM_AUTH_TOKEN']
    launcher = launch_ccm_cluster.CCMLauncher(ccm_token, github_label)
    start_stop_attempts = launch_ccm_cluster.CCMLauncher.DEFAULT_ATTEMPTS
    if 'CCM_ATTEMPTS' in os.environ:
        start_stop_attempts = int(os.environ['CCM_ATTEMPTS'])

    if not launch_config:
        launch_config = launch_ccm_cluster.StartConfig(private_agents=5)

    cluster_info = launch_ccm_cluster.start_cluster(launcher, github_label,
                                                    start_stop_attempts,
                                                    launch_config)
    print(cluster_info) # XXX
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
