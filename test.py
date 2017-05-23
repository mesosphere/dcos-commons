#!/usr/bin/env python3
import os
import sys

import argparse
import logging
import shutil
import string
import subprocess
import tempfile
import time

def get_repo_root():
    return os.path.dirname(sys.argv[0])

logger = logging.getLogger("dcos-commons-test")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")

sys.path.append(os.path.join(get_repo_root(), 'tools'))
import clustinfo
import fwinfo
import launch_ccm_cluster


work_dir = None
def get_work_dir():
    global work_dir
    if not work_dir:
        work_dir = tempfile.mkdtemp(prefix='test_workdir_', dir=get_repo_root())
        logger.info("Using %s for test run files", work_dir)
    return work_dir

work_dir = None
def get_work_dir():
    global work_dir
    if not work_dir:
        work_dir = tempfile.mkdtemp(prefix='test_workdir', dir=get_repo_root())
        logger.info("Using %s for test run files", work_dir)
    return work_dir

def parse_args(args=sys.argv):
    parser = argparse.ArgumentParser(description="Optionally build and test dcos-commons frameworks")
    parser.add_argument("--test-only", action='store_false', dest='run_build',
            help="requires a prior build")
    parser.add_argument("--build-only", action='store_false', dest='run_tests')
    parser.add_argument("--parallel", action='store_true',
            help="Use more than one cluster to try to speed up the tests")
    parser.add_argument("--cluster-count", type=int, default=2,
            help="Ignored unless using --parallel.  "
                 "Number of clusters to run tests on. "
                 "Large values are likely to break CCM.")
    parser.add_argument("--order", choices=('random', 'ordered'), default='random',
            help="Run tests in random order, or the order given on the command line.  "
            "In the case of no tests listed, ordered means alpha order.")
    parser.add_argument("--cluster-url", help="Use this already existing cluster, "
            "don't bring up new ones.")
    parser.add_argument("--cluster-token", help="Auth access when using cluster-url.")
    parser.add_argument("--cluster-teardown", choices=('success-only', 'always', 'never'),
            default='success-only',
            help="On test completion, shut down any cluster(s) automatically created.  "
            'For "success-only", test failures will leave the cluster running.')
    parser.add_argument("--continue-on-error", action='store_true',
            help="If a framework test fails, uninstall and keep going.  "
            "Default: stop all testing on a framework error.")
    parser.add_argument("test", nargs="*", help="Test or tests to run.  "
            "If no args provided, run all.")
    run_attrs = parser.parse_args()
    if (run_attrs.cluster_url or run_attrs.cluster_token) and not (
            run_attrs.cluster_url and run_attrs.cluster_token):
        raise Exception("Currently cluster url and token must be used together.")
    return run_attrs


class TestRequirementsNotMet(Exception):
    pass

class CommandFailure(Exception):
    pass

def detect_requirements(run_attrs):
    "Log all requirements met or not met, then throw exception if any not met"
    logger.info("Checking requirements")
    def have_command(cmd):
        if not shutil.which(cmd):
            logger.info("command %s ... missing. FAIL" % cmd)
            return False
        else:
            logger.info("command %s ... found." % cmd)
            return True

    def docker_works():
        exit_code = subprocess.call(['docker', 'ps'], stdout=subprocess.DEVNULL)
        if exit_code == 0:
            logger.info("docker is ... working.")
            return True
        else:
            logger.info("docker is ... not working.  FAIL")
            return False

    def have_or_can_create_cluster():
        if 'CLUSTER_URL' in os.environ:
            if 'CLUSTER_AUTH_TOKEN' in os.environ:
                logger.info("cluster env provided.")
                # TODO: try to access cluster
                return True
            else:
                logger.info("cluster url provided, but not auth token: TODO -- check if can access with cli.")
                logger.info("For now, proceeding with prayer")
                # TODO
                return True
        if 'CCM_AUTH_TOKEN' in os.environ:
            logger.info("ccm auth token provided.")
            return True
        else:
            logger.info("No CLUSTER_URL or CCM_AUTH_TOKEN provided. FAIL")
            return False

    def have_gopath():
        if 'GOPATH' in os.environ:
            logger.info("GOPATH is ... set.")
            return True
        else:
            logger.info("GOPATH is ... not set.  FAIL")
            return False

    def have_ssh_key():
        try:
            completed_cmd = subprocess.run(['ssh-add', '-l'], stdout=subprocess.PIPE)
            if completed_cmd.returncode != 0:
                logger.info("ssh-add invocation returned failure.  FAIL")
                return False
            if b'SHA256:i+SOiM8V5+yI3C0LoNlPaDk+ffCdOk2ZmDWoRIK8aw4' in completed_cmd.stdout:
                logger.info("ssh-agent .. has ec2 key.")
                return True
            else:
                logger.info("ssh-agent .. does not have ec2 key.  FAIL")
                return False
        except:
            logger.info("ssh-add invocation failed.  FAIL")
            return False

    results = {}

    # build requirements
    results['java'] = have_command("java")
    results['go'] = have_command("go")
    results['gopath'] = have_gopath()
    # TODO: verify libssl-dev or equivalent installed
    # In other words: #include <openssl/opensslv.h> should work

    if sys.platform != 'darwin':
        results['upx'] = have_command("upx")

    # upload requirements
    results['aws'] = have_command("aws")
    # TODO: verify can access our s3 bucket

    results['docker'] = have_command("docker")
    if results['docker']:
        results['docker_works'] = docker_works()
    # TODO: validate we have the docker access

    # test requirements
    results['virtualenv'] = have_command("virtualenv")
    results['cluster'] = have_or_can_create_cluster()
    results['ec2_ssh_key'] = have_ssh_key()
    results['jq'] = have_command("jq")

    failures = [v for v in results.values() if v==False]
    if failures:
        msg = "Requirements not met."
        logger.info(msg)
        raise TestRequirementsNotMet(msg, results)
    logger.info("All (tested) requirements met.")
    return results

def get_cluster():
    "Bring up a cluster and return, or return an already running cluster"
    pass

def setup_frameworks(run_attrs):
    # We should get the cluster version from the running cluster, when it
    # exists.  However there are two problems:
    # 1 - the busted certs we use give some python installs indigestion (this
    # could be worked around)
    # 2 - during dev cycles, the version is often wrong :-(

    # As a result, just use the env var supplied to most continuous
    # integration jobs, CCM_CHANNEL
    ccm_channel = os.environ.get("CCM_CHANNEL")
    version = None
    if ccm_channel and ccm_channel.startswith("testing/1"):
        testing , version = ccm_channel.split("/", 1)
        version_chars = set(version)
        valid_version_chars = set(string.digits + '.')
        if not version_chars.issubset(valid_version_chars):
            version = None

    if not run_attrs.test:
        fwinfo.autodiscover_frameworks(dcos_version=version)
    else:
        for framework in run_attrs.test:
            fwinfo.add_framework(framework, dcos_version=version)

    if run_attrs.order == "random":
        fwinfo.shuffle_order()

    fw_names = fwinfo.get_framework_names()
    logger.info("Frameworks initialized: %s", ", ".join(fw_names))


def _action_wrapper(action_name, framework, function, *args):
    framework.start_action(action_name)
    try:
        val = function(*args)
        framework.finish_action_ok(action_name)
        return val
    except:
        framework.finish_action_fail(action_name)
        raise

def build_and_upload(run_attrs=parse_args([])):
    """
    Build a list of framework scheduler and put them at URLs so a cluster can use it.
    build() and upload()should be two different functions, but that's a
    project for another day.

    run_attrs takes defaults from the argument parser with no arguments
    """
    for framework in fwinfo.get_frameworks():
        func = build_and_upload_single
        args = framework, run_attrs
        _action_wrapper("build %s" % framework.name,
                framework, func, *args)

# TODO: consider moving this to Nexus
def _upload_proxylite(framework):
    logger.info("trying to push proxylite to docker [1/2]")
    cmd_args = ['bash', 'frameworks/proxylite/scripts/ci.sh', 'pre-test']
    completed_cmd = subprocess.run(cmd_args)
    if completed_cmd.returncode == 0:
        logger.info("docker push succeeded.")
    else:
        logger.info("docker push failed; sleeping 5 seconds (XXX)")
        time.sleep(5)
        logger.info("trying to push proxylite to docker [2/2]")
        completed_cmd = subprocess.run(cmd_args)
        if completed_cmd.returncode == 0:
            logger.info("docker push succeeded.")
        else:
            logger.info("docker push failed; aborting proxylite test")
            raise CommandFailure(cmd_args)
    logger.info("Push of proxylite to docker complete.")

def _make_url_path(framework):
    return os.path.join(framework.dir, "%s-framework-url" % framework.name)

def _build_upload_aws(framework):
    # Gross hack to just get a return value, hopfully kill this soon.
    custom_env = os.environ.copy()
    url_textfile_path = _make_url_path(framework)
    if os.path.isfile(url_textfile_path):
        logger.info("Removing stale url textfile (%s) from prior run", url_textfile_path)
        os.unlink(url_textfile_path)
    custom_env['UNIVERSE_URL_PATH'] = url_textfile_path

    logger.info("Building %s and uploading to aws.", framework.name)
    cmd_args = [framework.buildscript, 'aws']
    completed_cmd = subprocess.run(cmd_args, env=custom_env)
    if completed_cmd.returncode != 0:
        msg = "%s invocation returned failure.  FAIL" % framework.buildscript
        logger.info("build & push script failed: %s, aborting %s test", msg, framework.name)
        raise CommandFailure(cmd_args)
    if not os.path.isfile(url_textfile_path):
        template = "%s failed to create output url textfile %s.  FAIL"
        msg = template % (framework.buildscript, framework.name)
        raise CommandFailure(cmd_args)
    with open(url_textfile_path) as url_file:
        # nearly always one url, but sometimes more
        stub_urls = url_file.read().splitlines()
    framework.stub_universe_urls = stub_urls

def _recover_stub_urls(run_attrs, repo_root):
    """If run with test_only, acquire the stub_universe urls from the
    filesystem.
    Will fail with exception if they're not present.
    """
    for framework in fwinfo.get_frameworks():
        url_textfile_path = _make_url_path(framework)
        try:
            with open(url_textfile_path) as url_file:
                stub_urls = url_file.read().splitlines()
            framework.stub_universe_urls = stub_urls
        except:
            logger.error("Failed to open universe url_file=%s for framework=%s",
                    url_textfile_path, framework.name)
            raise


def build_and_upload_single(framework, run_attrs):
    """Build a framework scheduler and put it at URL so a cluster can use it.
    build() and upload()should be two different functions, but that's a
    project for another day.
    """
    logger.info("Starting build & upload for %s", framework.name)

    if framework.name == 'proxylite':
        func = _upload_proxylite
        args = framework,
        _action_wrapper("upload proxylite",
                framework, func, *args)

    # TODO handle stub universes?  Only for single?

    # TODO build and push should probably be broken out as two recorded actions
    func = _build_upload_aws
    args = framework,
    _action_wrapper("upload %s to aws" % framework.name,
            framework, func, *args)

    logger.info("Built/uploaded framework=%s stub_universe_urls=%s.",
            framework.name, framework.stub_universe_urls)


def setup_clusters(run_attrs):
    if not run_attrs.parallel:
        count = 1
    else:
        count = run_attrs.cluster_count
    if count == 1 and run_attrs.cluster_url and run_attrs.cluster_token:
        clustinfo.add_running_cluster(run_attrs.cluster_url,
                run_attrs.cluster_token)
        return
    elif count > 1 and (run_attrs.cluster_url):
        sys.exit("Sorry, no support for multiple externally set up clusters yet.")
    for i in range(count):
        human_count = i+1
        clustinfo.start_cluster(reporting_name="cluster number %s" % human_count)

def teardown_clusters():
    logger.info("Shutting down all clusters.")
    try:
        clustinfo.shutdown_clusters()
    except Exception as e:
        logger.exception("Cluster teardown did not run correctly, ignoring.")

def _one_cluster_linear_tests(run_attrs, repo_root, continue_on_error):
    fail_fast = not continue_on_error
    if run_attrs.cluster_url and run_attrs.cluster_token:
        clustinfo.add_running_cluster(run_attrs.cluster_url,
                                      run_attrs.cluster_token)
    else:
        start_config = launch_ccm_cluster.StartConfig(private_agents=6)
        clustinfo.start_cluster(start_config)

    cluster = clustinfo._clusters[0]
    for framework in fwinfo.get_frameworks():
        func = run_test
        args = framework, cluster, repo_root
        _action_wrapper("Run %s tests" % framework.name,
                framework, func, *args)
    # we don't handle exceptions here, so any failures will stop us from
    # getting this far.
    all_passed = True
    return all_passed

def _handle_test_completions():
    all_tests_ok = True
    for framework in fwinfo.get_frameworks():
        if not framework.running:
            # never started
            continue
        pollval = framework.popen.poll()
        if pollval == None:
            # probably still running; try again later
            continue
        action_name = "Test %s completed" % framework.name
        framework.start_action(action_name)
        logger.info("%s test exit code: %s", framework.name, pollval)
        if pollval == 0:
            # test exited with success
            logger.info("%s tests completed successfully.  PASS",
                        framework.name)
        else:
            logger.info("%s tests failed.  FAILED", framework.name)
            all_tests_ok = False

        framework.running = False
        logger.info("%s unclaiming cluster id %s", framework.name,
                framework.cluster.cluster_id)
        framework.cluster.unclaim(framework)
        framework.cluster = None

        logger.info("%s test output follows ------------>>>>>>", framework.name)
        framework.output_file.seek(0)
        for line in framework.output_file:
            sys.stdout.buffer.write(line)
        sys.stdout.flush()
        framework.output_file.close()
        logger.info("<<<<<<------------ end %s test output", framework.name)

        if pollval == 0:
            framework.finish_action_ok(action_name)
        else:
            framework.finish_action_fail(action_name)

    return all_tests_ok


def _multicluster_linear_per_cluster(run_attrs, repo_root, continue_on_error):
    fail_fast = not continue_on_error
    test_list = list(fwinfo.get_framework_names())
    next_test = None
    all_ok = True
    try:
        while True:
            # acquire the next test, if there is one
            if not next_test and test_list:
                next_test = test_list.pop(0)
                logger.info("Next test to run: %s", next_test)
            if next_test:
                # we have a test to run, find a cluster to run it on.
                avail_cluster = clustinfo.get_idle_cluster()
                logger.debug("avail_cluster=%s", avail_cluster)
                if not avail_cluster and clustinfo.running_count() < run_attrs.cluster_count:
                    # we're supposed to start more clusters, so do so
                    human_count = clustinfo.running_count()+1
                    logger.info("Launching cluster %s towards count %s",
                                  human_count, run_attrs.cluster_count)
                    # TODO: retry cluster launches
                    start_config = launch_ccm_cluster.StartConfig(private_agents=6)
                    avail_cluster = clustinfo.start_cluster(start_config,
                            reporting_name="Cluster %s" % human_count)
                elif not avail_cluster:
                    # We're not supposed to start more clusters, so wait, and
                    # check for test completion.
                    info_bits = []
                    for cluster in clustinfo._clusters:
                        template = "cluster_id=%s in use by frameworks=%s"
                        info_bits.append(template % (cluster.cluster_id,
                                                     cluster.in_use()))
                    logger.info("Waiting for cluster to launch %s; %s",
                                  next_test, ", ".join(info_bits))
                    # TODO: report .out sizes for running tests
                    time.sleep(30) # waiting for an available cluster
                    # meanwhile, a test might finish
                    run_ok = _handle_test_completions()
                    if not run_ok:
                        all_ok = False
                        if fail_fast:
                            logger.info("Some tests failed; aborting early") # TODO paramaterize
                            break
                    continue

                # At this point, we have a cluster and a test, so start it.
                logger.info("Testing framework=%s in background on cluster=%s.",
                             next_test, avail_cluster.cluster_id)
                framework = fwinfo.get_framework(next_test)
                func = start_test_background
                args = framework, avail_cluster, repo_root
                _action_wrapper("Launch %s tests" % framework.name,
                        framework, func, *args)
                next_test = None
                avail_cluster = None
            else:
                # No tests left, handle completion and waiting for completion.
                if not fwinfo.running_frameworks():
                    logger.info("No framework tests running.  All done.")
                    break # all tests done
                logger.info("No framework tests to launch, waiting for completions.")
                # echo status
                time.sleep(30) # waiting for tests to complete

            # after launching a test, or waiting, check for test completion.
            run_ok = _handle_test_completions()
            if not run_ok:
                all_ok = False
                if fail_fast:
                    logger.info("Some tests failed; aborting early") # TODO paramaterize
                    break
            all_ok = _handle_test_completions()
            if fail_fast and not all_ok:
                logger.info("Some tests failed; aborting early") # TODO paramaterize
                break
    finally:
        # TODO probably should also make this teardown optional
        for framework_name in fwinfo.get_framework_names():
            framework = fwinfo.get_framework(framework_name)
            if framework.popen:
                logger.info("Sending SIGTERM to subprocess for framework=%s, if still running",
                             framework_name)
                framework.popen.terminate() # does nothing if already completed
    return all_ok

def run_tests(run_attrs, repo_root):
    logger.info("cluster_teardown policy: %s", run_attrs.cluster_teardown)
    try: # all clusters are set up inside this try
        all_passed = False
        if run_attrs.parallel:
            logger.debug("Running multicluster test run")
            all_passed = _multicluster_linear_per_cluster(run_attrs, repo_root,
                                                          run_attrs.continue_on_error)
        else:
            all_passed = _one_cluster_linear_tests(run_attrs, repo_root,
                                                   run_attrs.continue_on_error)
        if not all_passed:
            raise Exception("Some tests failed.")
    finally:
        if run_attrs.cluster_teardown == "always":
            teardown_clusters()
        elif run_attrs.cluster_teardown == "success-only" and all_passed:
            teardown_clusters()

        for cluster in clustinfo._clusters:
            logger.debug("Cluster still running: url=%s id=%s auth_token=%s",
                         cluster.url, cluster.cluster_id, cluster.auth_token)


def _setup_strict(framework, cluster, repo_root):
    "Do cruft required to create the roles for the framework"
    security = os.environ.get('SECURITY', '')
    logger.info("SECURITY set to: '%s'", security)
    if security == "strict":
        logger.info("running %s role strict setup script(s)", framework.name)
        perm_setup_script = os.path.join(repo_root, 'tools', 'setup_permissions.sh')

        custom_env = os.environ.copy()
        custom_env['CLUSTER_URL'] = cluster.url
        custom_env['CLUSTER_AUTH_TOKEN'] = cluster.auth_token

        for script_num in (1, 2):
            role_arg = '%s%s-role' % (framework.name, script_num)
            cmd_args = [perm_setup_script, 'root', role_arg]

            completed_cmd = subprocess.run(cmd_args, env=custom_env)
            if completed_cmd.returncode != 0:
                msg = "%s invocation returned failure.  FAIL"
                logger.info(msg, " ".join(cmd_args))
                raise CommandFailure(cmd_args)

        logger.info("%s role setup script(s) completed", framework.name)

def start_test_background(framework, cluster, repo_root):
    """Start one test on a cluster as a subprocess.
    The state of these subprocesses lives in the framework objects stored in
    the fwinfo module
    """
    logger.info("Starting cluster configure & test run for %s (will background)",
                framework.name)
    _setup_strict(framework, cluster, repo_root)

    logger.info("Launching shakedown in background for %s", framework.name)
    logger.debug("stub_universe_urls:%s cluster_url:%s authtoken:%s",
            framework.stub_universe_urls, cluster.url, cluster.auth_token)

    custom_env = os.environ.copy()
    custom_env['TEST_GITHUB_LABEL'] = framework.name
    custom_env['CLUSTER_URL'] = cluster.url
    custom_env['CLUSTER_AUTH_TOKEN'] = cluster.auth_token

    runtests_script = os.path.join(repo_root, 'tools', 'run_tests.py')


    # Why this trailing slash here? no idea.
    framework_testdir = os.path.join(framework.dir, 'tests') + "/"
    cmd_args = [runtests_script, 'shakedown', framework_testdir]
    for stub_url in framework.stub_universe_urls:
        cmd_args.extend(["--stub-universe", stub_url])

    output_filename = os.path.join(get_work_dir(), "%s.out" % framework.name)
    output_file = open(output_filename, "w+b")
    popen_obj = subprocess.Popen(cmd_args, stdout=output_file,
                                 stderr=output_file, env=custom_env)

    framework.running = True
    framework.popen = popen_obj
    framework.output_file = output_file
    framework.cluster = cluster
    cluster.claim(framework)
    logger.info("Shakedown for %s now running in background", framework.name)

def run_test(framework, cluster, repo_root):
    "Run one test on a cluster in a blocking fashion"
    logger.info("Starting cluster configure & test run for %s", framework.name)
    _setup_strict(framework, cluster, repo_root)

    logger.info("Launching shakedown for %s", framework.name)
    logger.debug("stub_universes:%s cluster_url:%s authtoken:%s",
            framework.stub_universe_urls, cluster.url, cluster.auth_token)
    custom_env = os.environ.copy()
    custom_env['TEST_GITHUB_LABEL'] = framework.name
    custom_env['CLUSTER_URL'] = cluster.url
    custom_env['CLUSTER_AUTH_TOKEN'] = cluster.auth_token
    runtests_script = os.path.join(repo_root, 'tools', 'run_tests.py')
    # Why this trailing slash here? no idea.
    framework_testdir = os.path.join(framework.dir, 'tests') + "/"
    cmd_args = [runtests_script, 'shakedown', framework_testdir]
    for stub_url in framework.stub_universe_urls:
        cmd_args.extend(["--stub-universe", stub_url])
    completed_cmd = subprocess.run(cmd_args, env=custom_env)
    if completed_cmd.returncode != 0:
        msg = "Test script: %s invocation returned failure for %s.  FAIL"
        logger.info(msg, runtests_script, framework.name)
        raise CommandFailure(cmd_args)

def report_failed_actions():
    "Do useful things with the recorded successful and failed actions"
    # These are our data sources
    cluster_launch_attempts = clustinfo.get_launch_attempts()
    _ = cluster_launch_attempts
    for framework in fwinfo.get_frameworks():
        actions = framework.actions
        _ = actions
    # We actually have no functionality to report them right now.
    pass

def main():
    run_attrs = parse_args()
    try:
        detect_requirements(run_attrs)
    except TestRequirementsNotMet:
        logger.error("Aborting run.")
        return False

    repo_root = get_repo_root()
    fwinfo.init_repo_root(repo_root)

    setup_frameworks(run_attrs)

    try:
        if run_attrs.run_build:
            build_and_upload(run_attrs)

        if run_attrs.run_tests:
            # if we're only testing, use stub_universes from before (they're
            # normally calculated during the build)
            if not run_attrs.run_build:
                _recover_stub_urls(run_attrs, repo_root)
            run_tests(run_attrs, repo_root)
    finally:
        report_failed_actions()
    return True

if __name__ == "__main__":
    if not main():
        sys.exit(1)
