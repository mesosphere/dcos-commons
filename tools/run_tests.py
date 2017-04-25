#!/usr/bin/env python3

import json
import logging
import os
import os.path
import random
import shutil
import string
import subprocess
import sys
import tempfile

import cli_install
import dcos_login
import github_update

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


class CITester(object):

    def __init__(self, dcos_url, github_label, sandbox_path='', cli_path=None):
        self._dcos_url = dcos_url
        self._sandbox_path = sandbox_path
        self._cli_path = cli_path
        self._github_updater = github_update.GithubStatusUpdater('test:{}'.format(github_label))


    def _configure_cli_sandbox(self):
        if not self._sandbox_path:
            preexisting_testtmp = os.environ.get('TESTRUN_TEMPDIR')
            if preexisting_testtmp:
                self._sandbox_path = tempfile.mkdtemp(prefix='ci-test-',
                        dir=preexisting_testtmp)
            else:
                self._sandbox_path = tempfile.mkdtemp(prefix='ci-test-')
        custom_env = {}
        # ask for unbuffered stdout, since test output randomly uses stdout
        # vs stderr
        custom_env['PYTHONUNBUFFERED'] = "yes"
        # must be custom for CLI to behave properly:
        custom_env['HOME'] = self._sandbox_path
        # prepend HOME (where CLI binary is downloaded) to PATH:
        custom_env['PATH'] = '{}:{}'.format(self._sandbox_path, os.environ['PATH'])
        # must be explicitly provided for CLI to behave properly:
        custom_env['DCOS_CONFIG'] = os.path.join(self._sandbox_path, 'cli-config')
        # optional:
        #custom_env['DCOS_DEBUG'] = custom_env.get('DCOS_DEBUG', 'true')
        #custom_env['DCOS_LOG_LEVEL'] = custom_env.get('DCOS_LOG_LEVEL', 'debug')
        logger.info('Created CLI sandbox: {}, Custom envvars: {}.'.format(self._sandbox_path, custom_env))
        for k, v in custom_env.items():
            os.environ[k] = v


    def _download_cli_to_sandbox(self):
        # TODO: provide non-env interface to copy a dcos cli
        local_path = os.environ.get('DCOS_CLI_PATH')
        if not local_path:
            src_tmpdir = os.environ.get('TESTRUN_TEMPDIR')
            if src_tmpdir:
                return cli_install.install_cli_from_dir(src_tmpdir, self._sandbox_path)
        if local_path:
            cli_filepath = cli_install.install_cli(local_path, self._sandbox_path)
        else:
            cli_filepath = cli_install.download_cli(self._dcos_url, self._sandbox_path)
        return cli_filepath


    def _configure_cli(self, dcos_url):
        cmds = [
            'which dcos',
            'dcos config set core.dcos_url "{}"'.format(dcos_url),
            'dcos config set core.reporting True',
            'dcos config set core.ssl_verify false',
            'dcos config set core.timeout 5',
            'dcos config show']
        for cmd in cmds:
            subprocess.check_call(cmd.split())


    def setup_cli(self, stub_universes = {}):
        try:
            self._github_updater.update('pending', 'Setting up CLI')
            self._configure_cli_sandbox()  # adds to os.environ
            cli_filepath = self._download_cli_to_sandbox()
            self._configure_cli(self._dcos_url)
            dcos_login.DCOSLogin(self._dcos_url).login()
            # check for any preexisting universes and remove them -- the cluster requires no duplicate uris
            if stub_universes:
                logger.info('Checking for duplicate stub universes')
                cur_universes = subprocess.check_output('dcos package repo list --json'.split()).decode('utf-8')
                for repo in json.loads(cur_universes)['repositories']:
                    # {u'name': u'Universe', u'uri': u'https://universe.mesosphere.com/repo'}
                    if repo['uri'] in stub_universes.values():
                        logger.info('Removing duplicate repository: {} {}'.format(repo['name'], repo['uri']))
                        subprocess.check_call('dcos package repo remove {}'.format(repo['name']).split())
                for name, url in stub_universes.items():
                    logger.info('Adding repository: {} {}'.format(name, url))
                    subprocess.check_call('dcos package repo add --index=0 {} {}'.format(name, url).split())
        except:
            self._github_updater.update('error', 'CLI Setup failed')
            raise


    def run_shakedown(self, test_dirs, requirements_txt=None, pytest_types='sanity'):
        normal_path = test_dirs.rstrip(os.sep)
        framework = os.path.basename(os.path.dirname(normal_path))
        # keep virtualenv in a consistent/reusable location:
        if 'WORKSPACE' in os.environ:
            logger.info("Detected running under Jenkins; will tell shakedown to emit junit-style xml.")
            virtualenv_path = os.path.join(os.environ['WORKSPACE'], framework, 'shakedown_env')
            # produce test report for consumption by Jenkins:
            path_based_name = "%s-%s" % (framework, "shakedown-report.xml")
            jenkins_args = '--junitxml=' + path_based_name
        else:
            virtualenv_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                           framework, 'shakedown_env')
            jenkins_args = ''
        if requirements_txt is not None:
            logger.info('Using provided requirements.txt: {}'.format(requirements_txt))
        else:
            # generate default requirements:
            logger.info('No requirements.txt provided, using default requirements')
            requirements_txt = os.path.join(self._sandbox_path, 'requirements.txt')
            requirements_file = open(requirements_txt, 'w')
            requirements_file.write('''
requests==2.10.0

-e git+https://github.com/dcos/shakedown.git@master#egg=shakedown
''')
            requirements_file.flush()
            requirements_file.close()
        # to ensure the 'source' call works, just create a shell script and execute it directly:
        script_path = os.path.join(self._sandbox_path, 'run_shakedown.sh')
        script_file = open(script_path, 'w')
        # TODO(nick): remove this inlined script with external templating
        #             (or find a way of entering the virtualenv that doesn't involve a shell script)
        script_file.write('''
#!/bin/bash
set -e
echo "VIRTUALENV CREATE/UPDATE: {venv_path}"
virtualenv -p $(which python3) --always-copy {venv_path}
echo "VIRTUALENV ACTIVATE: {venv_path}"
source {venv_path}/bin/activate
echo "REQUIREMENTS INSTALL: {reqs_file}"
pip install -r {reqs_file}
echo "SHAKEDOWN RUN: {test_dirs} FILTER: {pytest_types}"
py.test {jenkins_args} -vv --fulltrace -x -s -m "{pytest_types}" {test_dirs}
'''.format(venv_path=virtualenv_path,
           reqs_file=requirements_txt,
           dcos_url=self._dcos_url,
           jenkins_args=jenkins_args,
           pytest_types=pytest_types,
           test_dirs=test_dirs))
        script_file.flush()
        script_file.close()
        try:
            self._github_updater.update('pending', 'Running shakedown tests')
            subprocess.check_call(['bash', script_path])
            self._github_updater.update('success', 'Shakedown tests succeeded')
        except:
            self._github_updater.update('failure', 'Shakedown tests failed')
            raise

    def run_dcostests(self, test_dirs, dcos_tests_dir, pytest_types='sanity'):
        os.environ['DOCKER_CLI'] = 'false'
        normal_path = test_dirs.rstrip(os.sep)
        framework = os.path.basename(os.path.dirname(normal_path))
        if 'WORKSPACE' in os.environ:
            logger.info("Detected running under Jenkins; will tell shakedown to emit junit-style xml.")
            virtualenv_path = os.path.join(os.environ['WORKSPACE'], framework, 'dcostests_env')
            # produce test report for consumption by Jenkins:
            path_based_name = "%s-%s" % (framework, "dcostests-report.xml")
            jenkins_args = '--junitxml=' + path_based_name
        else:
            virtualenv_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                           framework, 'dcostests_env')
            jenkins_args = ''
        # to ensure the 'source' call works, just create a shell script and execute it directly:
        script_path = os.path.join(self._sandbox_path, 'run_dcos_tests.sh')
        script_file = open(script_path, 'w')
        # TODO(nick): remove this inlined script with external templating
        #             (or find a way of entering the virtualenv that doesn't involve a shell script)
        script_file.write('''
#!/bin/bash
set -e
cd {dcos_tests_dir}
echo "{dcos_url}" > docker-context/dcos-url.txt
echo "VIRTUALENV CREATE/UPDATE: {venv_path}"
virtualenv -p $(which python3) --always-copy {venv_path}
echo "VIRTUALENV ACTIVATE: {venv_path}"
source {venv_path}/bin/activate
echo "REQUIREMENTS INSTALL: {reqs_file}"
pip install -r {reqs_file}
echo "DCOS-TEST RUN $(pwd): {test_dirs} FILTER: {pytest_types}"
SSH_KEY_FILE="" PYTHONPATH=$(pwd) py.test {jenkins_args} -vv -s -m "{pytest_types}" {test_dirs}
'''.format(venv_path=virtualenv_path,
           reqs_file=os.path.join(dcos_tests_dir, 'requirements.txt'),
           dcos_tests_dir=dcos_tests_dir,
           dcos_url=self._dcos_url,
           jenkins_args=jenkins_args,
           pytest_types=pytest_types,
           test_dirs=test_dirs))
        script_file.flush()
        script_file.close()
        try:
            self._github_updater.update('pending', 'Running dcos-tests')
            subprocess.check_call(['bash', script_path])
            self._github_updater.update('success', 'dcos-tests succeeded')
        except:
            self._github_updater.update('failure', 'dcos-tests failed')
            raise

    def delete_sandbox(self):
        if not self._sandbox_path:
            return  # no-op
        logger.info('Deleting CLI sandbox: {}'.format(self._sandbox_path))
        shutil.rmtree(self._sandbox_path)


def print_help(argv):
    logger.info('Syntax: TEST_TYPES="sanity or recovery" CLUSTER_URL="yourcluster.com" {} <"shakedown"|"dcos-tests"> <path/to/tests/> </path/to/requirements.txt | /path/to/dcos-tests>'.format(argv[0]))
    logger.info('  Example (shakedown): $ {} shakedown /path/to/your/tests/ [/path/to/your/requirements.txt]'.format(argv[0]))
    logger.info('  Example (dcos-tests, deprecated): $ {} dcos-tests /path/to/your/tests/ /path/to/dcos-tests/'.format(argv[0]))


def _rand_str(size):
    return ''.join(random.choice(string.ascii_lowercase + string.digits) for _ in range(size))


def main(argv):
    if len(argv) < 3:
        print_help(argv)
        return 1
    test_type = argv[1]
    test_dirs = argv[2]

    cluster_url = os.environ.get('CLUSTER_URL', '').strip('"').strip('\'')
    if cluster_url:
        logger.info('Using cluster URL from CLUSTER_URL envvar: {}'.format(cluster_url))
    else:
        # try getting the value from a CLI:
        try:
            cluster_url = subprocess.check_output('dcos config show core.dcos_url'.split()).decode('utf-8').strip()
            if len(cluster_url) == 0:
                raise Exception("Missing core.dcos_url") # to be caught below
            logger.info('Using cluster URL from CLI: {}'.format(cluster_url))
        except:
            logger.error('CLUSTER_URL envvar, or CLI in PATH with core.dcos_url, is required.')
            print_help(argv)
            return 1

    tester = CITester(cluster_url, os.environ.get('TEST_GITHUB_LABEL', test_type))

    stub_universes = {}
    stub_universe_url = os.environ.get('STUB_UNIVERSE_URL', '')
    if stub_universe_url:
        stub_universes['testpkg-' + _rand_str(8)] = stub_universe_url

    pytest_types = os.environ.get('TEST_TYPES', 'sanity')

    try:
        tester.setup_cli(stub_universes)
        if test_type == 'shakedown':
            if len(argv) >= 4:
                # use provided requirements.txt
                requirements_txt = argv[3]
            else:
                # use default requirements
                requirements_txt = None
            tester.run_shakedown(test_dirs, requirements_txt, pytest_types)
        elif test_type == 'dcos-tests':
            dcos_tests_dir = argv[3]
            tester.run_dcostests(test_dirs, dcos_tests_dir, pytest_types)
        else:
            raise Exception('Unsupported test type: {}'.format(test_type))

    finally:
        if not 'KEEP_SANDBOX' in os.environ:
            tester.delete_sandbox()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
