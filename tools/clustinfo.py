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
import json
import os
import subprocess
import time

import cli_install
import dcos_login
import launch_ccm_cluster

logger = logging.getLogger(__name__)

# holds info objects
_clusters = []


def _cluster_dir():
    cluster_num = len(_clusters)+1
    return "cluster_{}".format(cluster_num)

def start_cluster(temp_dir, launch_config=None, reporting_name=None):
    """Launch a new cluster and retain it in the set of known running clusters"""
    if not reporting_name:
        reporting_name="only_cluster"

    config_dir = os.path.join(temp_dir, _cluster_dir())

    _launch_recorder.start(reporting_name)
    try:
        cluster = _launch_cluster(config_dir, launch_config)
        _launch_recorder.finish_ok(reporting_name, cluster)
    except:
        _launch_recorder.finish_fail(reporting_name)
        raise
    _clusters.append(cluster)
    logger.info("Started cluster: %s", cluster.cluster_id)
    return cluster


def get_launch_attempts():
    return _launch_recorder.get_list()


def add_running_cluster(temp_dir, url):
    """Add an already running cluster to the set of known running clusters"""
    config_dir = os.path.join(temp_dir, _cluster_dir())
    cli_install.ensure_cli_downloaded(url, temp_dir)
    auth_token = dcos_login.DCOSLogin(url).get_acs_token()
    cluster = ClusterInfo(url, auth_token, config_dir, external=True)
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


def _launch_cluster(config_dir, launch_config=None):
    ccm_token = os.environ['CCM_AUTH_TOKEN']
    if not launch_config:
        launch_config = launch_ccm_cluster.StartConfig(private_agents=6)
    cluster_info = launch_ccm_cluster.start_cluster(ccm_token, launch_config)
    cluster = ClusterInfo(cluster_info["url"], cluster_info["auth_token"],
            config_dir, cluster_id=cluster_info["id"])
    return cluster


def fetch_diagnostics(tgt_dir):
    "Collect diagnostic informaion from all clusters"
    logger.info("Collecting diagnostics from clusters")
    for cluster in _clusters:
        cluster.start_diagnostics()
        logger.info("Spawned diagnostics on cluster: %s", cluster.url)

    for cluster in _clusters:
        while not cluster.diagnostics_complete():
            logger.info("Waiting for diagnostics on cluster %s", cluster.url)
            time.sleep(30)
        logger.info("Diagnostics complete on cluster: %s, downloading...", cluster.url)
        cluster.fetch_complete_diagnostics(tgt_dir)
        logger.info("Diagnostics stored in %s.", tgt_dir)
        logger.info("Retreiving logs for all tasks on cluster: %s ...", cluster.url)
        cluster.fetch_all_task_logs(tgt_dir)

class ClusterInfo(object):
    def __init__(self, url, auth_token, config_dir, cluster_id=None, external=False):
        self.url = url
        self.auth_token = auth_token
        self.config_dir = config_dir # a place for the dcos cli to keep its info
        self.cluster_id = cluster_id
        self.external = external # launched outside local automation
        self._frameworks_using = set()
        # self.node_count etc
        self.custom_env = {}     # for use by the dcos cli

        dcos_bindir = os.path.dirname(config_dir)

        self._cli_install(dcos_bindir)
        self.dcos_bin_loc = os.path.join(dcos_bindir, 'dcos')
        self._configure_cli()


    def claim(self, framework):
        self._frameworks_using.add(framework)

    def unclaim(self, framework):
        self._frameworks_using.remove(framework)

    def in_use(self):
        return self._frameworks_using

    def is_running(self):
        return True

    def _cmd_from_args(self, args):
        return [self.dcos_bin_loc] + args

    def dcoscli_run_yes(self, args):
        cmd = self._cmd_from_args(args)
        logger.debug("running (with yes); %s", " ".join(cmd))
        pobj = subprocess.Popen(cmd, stdin=subprocess.PIPE,
                env=self.custom_env)
        pobj.communicate(input=b"yes\n", timeout=400)

    def dcoscli_run_output(self, args, **kwargs):
        cmd = self._cmd_from_args(args)
        logger.debug("running; %s", " ".join(cmd))
        return subprocess.check_output(cmd, env=self.custom_env, **kwargs)

    def dcoscli_run(self, args):
        cmd = self._cmd_from_args(args)
        logger.debug("running; %s", " ".join(cmd))
        subprocess.check_call(cmd, env=self.custom_env)

    def _cli_install(self, bindir):
        logging.info("Installing cli to %s", bindir)
        cli_install.ensure_cli_downloaded(self.url, bindir)

    def _dcoscli_config_set(self, setting, value):
        """Set a dcos cli setting to a value"""
        args = ['config', 'set', setting, value]
        self.dcoscli_run(args)

    def _configure_cli(self):
        """Set up a dcos configuration file for ongoing use with the
        cluster."""
        self.custom_env['HOME'] = self.config_dir
        config_path = os.path.join(self.config_dir, 'cli-config')
        os.makedirs(self.config_dir)
        self.custom_env['DCOS_CONFIG'] = config_path

        self._dcoscli_config_set('core.dcos_url', self.url)
        self._dcoscli_config_set('core.ssl_verify', 'false')
        self._dcoscli_config_set('core.reporting', 'True') # What does this do?
        self._dcoscli_config_set('core.timeout', '10')

        self._dcoscli_config_set('core.dcos_acs_token', self.auth_token)

    def start_diagnostics(self):
        args = ["node", "diagnostics", "create", "all"]
        self.dcoscli_run(args)

    def diagnostics_complete(self):
        args = ["node", "diagnostics", "--status", "--json"]
        # doesn't return json on error; but returns 1 which will throw an
        # exception
        output = self.dcoscli_run_output(args)
        output_s = output.decode('utf-8')
        logger.info("diagnostics status output: %s", output_s)
        data = json.loads(output_s)
        # this awesome output could include an infinite number of previously
        # created diagnostics; though not in jenkins runs at least
        first_server, diagnostics_entry = data.popitem()
        if not diagnostics_entry:
            msg = "No diagnostics items in the json output"
            logger.error("%s: %s", msg, output)
            raise Exception(msg)
        logger.info("diagnostics_entry: %s", diagnostics_entry)
        if not diagnostics_entry['job_progress_percentage'] == 100:
            return False
        # this path is 100% useless
        bundle_path = diagnostics_entry['last_bundle_dir']
        self.bundle_name = os.path.basename(bundle_path)  # HACK
        return True

    def fetch_complete_diagnostics(self, tgt_dir):
        download_dir = os.path.join(tgt_dir, 'diagnostics')
        os.makedirs(download_dir)
        args = ['node', 'diagnostics', 'download', self.bundle_name,
               '--location', download_dir]
        self.dcoscli_run_yes(args)

    def fetch_all_task_logs(self, tgt_dir):
        download_dir = os.path.join(tgt_dir, 'task_logs')
        os.makedirs(download_dir)
        args = ['task', '--completed']
        output = self.dcoscli_run_output(args)
        lines = output.splitlines()
        failures = []
        for line in lines[1:]: # skip header
            name, host, user, state, task_id = line.split()
            for logtype in ('stdout', 'stderr'):
                task_id_s = task_id.decode('utf-8')
                filename = "%s.%s" % (task_id_s, logtype)

                # we must NOT provide --completed for running tasks, but MUST
                # provide it for completed tasks, and they can complete while
                # we're about to ask.  So just try both.

                args_for_running_task = ['task', 'log',
                            '--lines=99999', # cli does not have a get all
                            task_id_s, logtype]
                args_for_completed_task = ['task', 'log',
                            '--completed',   # cli requires to be told tasks are
                                             # completed in order to get their logs (???)
                            '--lines=99999', # cli does not have a get all
                            task_id_s, logtype]
                log_and_spew = None
                try:
                    log_and_spew = self.dcoscli_run_output(
                            args_for_running_task,
                            stderr=subprocess.STDOUT)
                except subprocess.CalledProcessError:
                    try:
                        log_and_spew = self.dcoscli_run_output(
                                args_for_completed_task,
                                stderr=subprocess.STDOUT)
                    except subprocess.CalledProcessError as e:
                        failures.append((task_id_s, logtype))
                        logger.debug("failed to fetch %s log for task %s, %s",
                                logtype, task_id, e)
                if log_and_spew:
                    write_path = os.path.join(download_dir, filename)
                    with open(write_path, "wb") as f:
                        f.write(log_and_spew)

        logger.info("Failed to collect the following log combinations: %s",
            failures)


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
