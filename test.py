#!/usr/bin/env python3
import os
import sys

import argparse
import logging
import shutil
import subprocess
import tempfile
import time

def get_repo_root():
    return os.path.dirname(sys.argv[0])

logger = logging.getLogger("dcos-commons-test")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")

sys.path.append(os.path.join(get_repo_root(), 'tools'))
sys.path.append(os.path.join(get_repo_root(), 'tools', 'contrib'))
import clustinfo
import fwinfo
import launch_ccm_cluster
import junit_xml
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
    parser.add_argument("test", nargs="*", help="Test or tests to run.  "
            "If no args provided, run all.")
    return parser.parse_args()

class TestRequirementsNotMet(Exception):
    pass

class CommandFailure(Exception):
    pass

def detect_requirements():
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
    if not run_attrs.test:
        fwinfo.autodiscover_frameworks()
    else:
        for framework in run_attrs.test:
            fwinfo.add_framework(framework)

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
    for framework_name in fwinfo.get_framework_names():
        framework = fwinfo.get_framework(framework_name)
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

def _build_upload_aws(framework):
    # Gross hack to just get a return value, hopfully kill this soon.
    custom_env = os.environ.copy()
    url_textfile_path = os.path.join(framework.dir, "%s-framework-url" % framework.name)
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
        stub_url = url_file.read().strip()
    framework.stub_universe_url = stub_url

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

    logger.info("Built/uploladed framework=%s stub_universe_url=%s.",
            framework.name, framework.stub_universe_url)


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
    clustinfo.shutdown_clusters()

def _one_cluster_linear_tests(run_attrs, repo_root):
    start_config = launch_ccm_cluster.StartConfig(private_agents=6)
    clustinfo.start_cluster(start_config)

    cluster = clustinfo._clusters[0]
    for framework in fwinfo.get_frameworks():
        func = run_test
        args = framework, cluster, repo_root
        _action_wrapper("Run %s tests" % framework.name,
                framework, func, *args)

def handle_test_completions():
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


def _multicluster_linear_per_cluster(run_attrs, repo_root):
    test_list = list(fwinfo.get_framework_names())
    next_test = None

    try:
        while True:
            if not next_test and test_list:
                next_test = test_list.pop(0)
                logger.info("Next test to run: %s", next_test)
            if next_test:
                avail_cluster = clustinfo.get_idle_cluster()
                logger.debug("avail_cluster=%s", avail_cluster)
                if not avail_cluster and clustinfo.running_count() < run_attrs.cluster_count:
                    human_count = clustinfo.running_count()+1
                    logger.info("Launching cluster %s towards count %s",
                                  human_count, run_attrs.cluster_count)
                    # TODO: retry cluster launches
                    start_config = launch_ccm_cluster.StartConfig(private_agents=6)
                    avail_cluster = clustinfo.start_cluster(start_config,
                            reporting_name="Cluster %s" % human_count)
                elif not avail_cluster:
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
                    all_ok = handle_test_completions()
                    if not all_ok:
                        logger.info("Some tests failed; aborting early") # TODO paramaterize
                        break
                    continue
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
                if not fwinfo.running_frameworks():
                    logger.info("No framework tests running.  All done.")
                    break # all tests done
                logger.info("No framework tests to launch, waiting for completions.")
                # echo status
                time.sleep(30) # waiting for tests to complete

            all_ok = handle_test_completions()
            if not all_ok:
                logger.info("Some tests failed; aborting early") # TODO paramaterize
                break
    finally:
        # TODO probably should also make this teardown optional
        for framework_name in fwinfo.get_framework_names():
            logger.info("Terminating subprocess for framework=%s", framework_name)
            framework = fwinfo.get_framework(framework_name)
            if framework.popen:
                framework.popen.terminate() # does nothing if already completed

def run_tests(run_attrs, repo_root):
    logger.info("cluster_teardown policy: %s", run_attrs.cluster_teardown)
    try:
        if run_attrs.parallel:
            logger.debug("Running m ulticluster test run")
            _multicluster_linear_per_cluster(run_attrs, repo_root)
        else:
            _one_cluster_linear_tests(run_attrs, repo_root)
        if run_attrs.cluster_teardown == "success-only":
            teardown_clusters()
    except:
        if run_attrs.cluster_teardown == "always":
            teardown_clusters()
        raise
    finally:
        for cluster in clustinfo._clusters:
            logger.debug("Cluster still running: url=%s id=%s auth_token=%s",
                         cluster.url, cluster.cluster_id, cluster.auth_token)


def _setup_strict(framework, cluster, repo_root):
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

    logger.info("Launching shakedown for %s", framework.name)

    custom_env = os.environ.copy()
    custom_env['TEST_GITHUB_LABEL'] = framework.name
    custom_env['STUB_UNIVERSE_URL'] = framework.stub_universe_url
    custom_env['CLUSTER_URL'] = cluster.url
    custom_env['CLUSTER_AUTH_TOKEN'] = cluster.auth_token

    runtests_script = os.path.join(repo_root, 'tools', 'run_tests.py')

    # Why this trailing slash here? no idea.
    framework_testdir = os.path.join(framework.dir, 'tests') + "/"
    cmd_args = [runtests_script, 'shakedown', framework_testdir]

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

    logger.info("launching shakedown for %s", framework.name)
    custom_env = os.environ.copy()
    custom_env['STUB_UNIVERSE_URL'] = framework.stub_universe_url
    custom_env['TEST_GITHUB_LABEL'] = framework.name
    custom_env['CLUSTER_URL'] = cluster.url
    custom_env['CLUSTER_AUTH_TOKEN'] = cluster.auth_token
    runtests_script = os.path.join(repo_root, 'tools', 'run_tests.py')
    # Why this trailing slash here? no idea.
    framework_testdir = os.path.join(framework.dir, 'tests') + "/"
    cmd_args = [runtests_script, 'shakedown', framework_testdir]
    completed_cmd = subprocess.run(cmd_args, env=custom_env)
    if completed_cmd.returncode != 0:
        msg = "Test script: %s invocation returned failure for %s.  FAIL"
        logger.info(msg, runtests_script, framework.name)
        raise CommandFailure(cmd_args)

def emit_junit_xml():
    launch_fake_testcases = []
    for launch_attempt in clustinfo.get_launch_attempts():
        attempt_duration = launch_attempt.end_time - launch_attempt.start_time
        fake_test = junit_xml.TestCase(launch_attempt.name,
                                       elapsed_sec=attempt_duration)
        if launch_attempt.launch_succeeded:
            fake_test.stdout = "Launch worked"
        else:
            fake_test.add_failure_info("Launch failed")
        launch_fake_testcases.append(fake_test)

    launch_suite = junit_xml.TestSuite("Cluster launches",
            launch_fake_testcases)

    fake_suites = []
    fake_suites.append(launch_suite)

    for framework in fwinfo.get_frameworks():
        framework_testcases = []
        for action_name, action in framework.actions.items():
            action_duration = action['finish'] - action['start']
            fake_test = junit_xml.TestCase(action_name,
                                           elapsed_sec=action_duration,
                                           stdout = action['stdout'],
                                           stderr = action['stderr'])
            if not action['ok']:
                message = action['error_message']
                if not message:
                    message = "%s failed" % action_name
                fake_test.add_failure_info(message, action['error_output'])
            framework_testcases.append(fake_test)
        framework_suite = junit_xml.TestSuite("%s actions" % framework.name,
                framework_testcases)
        fake_suites.append(framework_suite)

    with open("junit_testpy.xml", "w") as f:
        junit_xml.TestSuite.to_file(f, fake_suites)



def main():
    run_attrs = parse_args()
    try:
        detect_requirements()
    except TestRequirementsNotMet:
        logger.error("Aborting run.")
        return

    repo_root = get_repo_root()
    fwinfo.init_repo_root(repo_root)

    setup_frameworks(run_attrs)

    try:
        if run_attrs.run_build:
            build_and_upload(run_attrs)

        if run_attrs.run_tests:
            run_tests(run_attrs, repo_root)
    finally:
        emit_junit_xml()

if __name__ == "__main__":
    main()
