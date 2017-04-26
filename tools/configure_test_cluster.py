#!/usr/bin/env python3

import logging
import os
import os.path
import shutil
import subprocess
import sys
import tempfile

import cli_install
import dcos_login
import venvutil

logger = logging.getLogger(__name__)
if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(message)s")

def _tools_dir():
    return os.path.dirname(os.path.realpath(__file__))

def _run_script(scriptname, args = [], **kwargs):
    logger.info('Command: {} {}'.format(scriptname, ' '.join(args)))
    script_path = os.path.join(_tools_dir(), scriptname)
    # redirect stdout to stderr:
    subprocess.check_call(['bash', script_path] + args, stdout=sys.stderr,
                          **kwargs)


class ClusterInitializer(object):
    def __init__(self, ccm_cluster_id, aws_stack_id, auth_token, dns_address,
            is_enterprise=False, security=None):
        self.ccm_cluster_id = ccm_cluster_id
        self.aws_stack_id = aws_stack_id
        self.auth_token = auth_token
        self.dns_address = dns_address
        self.is_enterprise = is_enterprise
        self.security = security
        if not security:
            self.dcos_url = 'http://%s' % dns_address
        else:
            self.dcos_url = 'https://%s' % dns_address
        self._install_cli()


    def create_mount_volumes(self):
        fmt = 'Enabling mount volumes for cluster {} (stack id {})'
        logger.info(fmt.format(self.ccm_cluster_id, self.aws_stack_id))
        import enable_mount_volumes
        # fabric spams to stdout, which causes problems with launch_ccm_cluster.
        # force total redirect to stderr:
        enable_mount_volumes.main(self.aws_stack_id, stdout=sys.stderr)

    def _install_cli(self):
        # create_service_account relies on dcos cli, which we may not have
        # at this point.
        self.cli_tempdir = os.environ.get('TESTRUN_TEMPDIR')
        if not self.cli_tempdir:
            self.cli_tempdir = tempfile.mkdtemp(prefix="conf_cluster")
        cli_install.ensure_cli_downloaded(self.dcos_url, self.cli_tempdir)

    def _run_shellscript_with_cli(self, script, args):
        custom_env = os.environ.copy()
        custom_env['PATH'] = self.cli_tempdir + os.pathsep + os.environ['PATH']

        _run_script(script, args, env=custom_env)

    def __del__(self):
        # clean up if we created it.
        if self.cli_tempdir and not 'TESTRUN_TEMPDIR' in os.environ:
            shutil.rmtree(self.cli_tempdir)

    def create_service_account(self):
        if self.security != 'strict':
            fmt ="Skipping creation of service account for security mode {}"
            logger.info(fmt.format(self.security))
            return

        fmt = 'Setting up permissions for cluster {} (stack id {})'
        logger.info(fmt.format(self.ccm_cluster_id, self.aws_stack_id))

        self._run_shellscript_with_cli('create_service_account.sh', [self.dcos_url, self.auth_token, '--strict'])

#   def setup_roles(self):
        # Examples of what individual tests should run. See respective projects' "test.sh":
        #_run_script('setup_permissions.sh', 'nobody cassandra-role'.split())
        #_run_script('setup_permissions.sh', 'nobody hdfs-role'.split())
        #_run_script('setup_permissions.sh', 'nobody kafka-role'.split())
        #_run_script('setup_permissions.sh', 'nobody spark-role'.split())

    def _initialize_dcos_cli(self):
        logger.info("Initializing dcos config")
        subprocess.check_call(['which', 'dcos'])
        subprocess.check_call(['dcos', 'config', 'set', 'core.dcos_url', self.dcos_url])
        subprocess.check_call(['dcos', 'config', 'set', 'core.reporting', 'True'])
        subprocess.check_call(['dcos', 'config', 'set', 'core.ssl_verify', 'False'])
        subprocess.check_call(['dcos', 'config', 'set', 'core.timeout', '5'])
        subprocess.check_call(['dcos', 'config', 'show'])
        dcos_login.DCOSLogin(self.dcos_url).login()

    def configure_master_settings(self):
        logger.info("Live-customizing mesos master")
        venv_path = venvutil.shared_tools_venv()
        venvutil.create_dcoscommons_venv(venv_path)
        venvutil.activate_venv(venv_path)

        # import delayed until dependencies exist
        import modify_master
        modify_master.set_local_infinity_defaults()

    def apply_default_config(self, initmaster=True):
        saved_env = os.environ.copy()
        try:
            # TODO; track a cluster-specific working dir, and keep this in
            # there; or use 1.10 features of dcos-cli to just specify a
            # configfile if shakedown will allow it; or figure out how to ssh
            # to the master using the cli, bypassing shakedown
            with tempfile.NamedTemporaryFile() as config_f:

                os.environ['DCOS_CONFIG'] = config_f.name
                os.environ['PATH'] = self.cli_tempdir + os.pathsep + os.environ['PATH']

                # redirect stdout at os level to avoid too much script
                # surgery at once; will remove once we remove the stdout
                # requirement of launch_ccm_cluster
                stdout_fd = sys.stdout.fileno()
                stdout_back = os.dup(stdout_fd)
                try:
                    os.dup2(sys.stderr.fileno(), stdout_fd)

                    self.create_service_account()
                    if initmaster:
                        # currently, the create_service_account.sh script sets up the
                        # cli itself so we initialize it in the style that test logic
                        # expects after.
                        # in the shiny future, set up the CLI once for the whole run.
                        self._initialize_dcos_cli()
                        self.configure_master_settings()
                finally:
                    sys.stdout.flush()
                    os.dup2(stdout_back, stdout_fd)
        finally:
            os.environ.clear()
            os.environ.update(saved_env)

# TODO: figure out how to determine all the necessary values from
# CLUSTER_URL etc
