#!/usr/bin/python3

import logging
import os
import os.path
import subprocess
import sys
import tempfile

import cli_install
import dcos_login
import venvutil

# Things this needs: cluster_id, stack_id (???), auth_token, dns_address,
# strict mode or not

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")

def _tools_dir():
    return os.path.dirname(os.path.realpath(__file__))

def _run_script(scriptname, args = []):
    logger.info('Command: {} {}'.format(scriptname, ' '.join(args)))
    script_path = os.path.join(_tools_dir(), scriptname)
    # redirect stdout to stderr:
    subprocess.check_call(['bash', script_path] + args, stdout=sys.stderr)


class ClusterInitializer(object):
    def __init__(self, cluster_id, stack_id, auth_token, dns_address,
            enterprise=False, security=None):
        self.cluster_id = cluster_id
        self.stack_id = stack_id
        self.auth_token = auth_token
        self.dns_address = dns_address
        self.entperise = enterprise
        self.security = security
        if not security:
            self.dcos_url = 'http://%s' % dns_address
        else:
            self.dcos_url = 'https://%s' % dns_address
        self._install_cli()


    def create_mount_volumes(self):
        fmt = 'Enabling mount volumes for cluster {} (stack id {})'
        logger.info(fmt.format(self.cluster_id, self.stack_id))
        import enable_mount_volumes
        # fabric spams to stdout, which causes problems with launch_ccm_cluster.
        # force total redirect to stderr:
        enable_mount_volumes.main(self.stack_id, stdout=sys.stderr)

    def _install_cli(self):
        # create_service_account relies on dcos cli, which we may not have
        # at this point.
        self.cli_tempdir = tempfile.mkdtemp(prefix="conf_cluster")
        cli_install.download_cli(dcos_url, self.cli_tempdir)

    def _run_shellscript_with_cli(script, args, cmd)
        custom_env = os.environ[:]
        custom_env['PATH'] = self.cli_tempdir + os.pathsep + os.environ['PATH']

        _run_script(script, args, env=custom_env)

    def __del__(self):
        if self.cli_tempdir:
            shutil.rmtree(self.cli_tempdir)

    def create_service_account(self):
        if self.security != 'strict':
            fmt ="Skipping creation of service account for security mode {}"
            logger.info(fmt.format(self.security))
            return

        fmt = 'Setting up permissions for cluster {} (stack id {})'
        logger.info(fmt.format(self.cluster_id, self.stack_id))

        _run_shellscript_with_cli('create_service_account.sh', [self.dcos_url, self.auth_token, '--strict'])

#   def setup_roles(self):
        # Examples of what individual tests should run. See respective projects' "test.sh":
        #_run_script('setup_permissions.sh', 'nobody cassandra-role'.split())
        #_run_script('setup_permissions.sh', 'nobody hdfs-role'.split())
        #_run_script('setup_permissions.sh', 'nobody kafka-role'.split())
        #_run_script('setup_permissions.sh', 'nobody spark-role'.split())

    def configure_master_settings(self):
        saved_env = os.environ.copy()
        try:
            # TODO; track a cluster-specific working dir, and keep this in
            # there; or use 1.10 features of dcos-cli to just specify a
            # configfile if shakedown will allow it; or figure out how to ssh
            # to the master using the cli, bypassing shakedown
            with tempfile.NamedTemporaryFile() as config_f:

                os.environ['DCOS_CONFIG'] = config_f.name

                subprocess.check_call(['which', 'docs'])
                subprocess.check_call(['dcos' 'config', 'set', 'core.dcos_url', self.dcos_url])
                subprocess.check_call(['dcos' 'config', 'set', 'core.reporting', 'True'])
                subprocess.check_call(['dcos' 'config', 'set', 'core.ssl_verify', 'False'])
                subprocess.check_call(['dcos' 'config', 'set', 'core.timeout', '5'])
                subprocess.check_call(['dcos' 'config', 'show'])
                dcos_login.DCOSLogin(self.dcos_url).login()

                venv_path = venvutil.shared_tools_venv()
                requirements_file = os.path.join(_tools_dir(), 'requirements.txt')
                # needs shakedown, so needs python3
                if sys.version_info < (3,4):
                    venvutil.create_venv(venv_path, py3=True)
                    venvutil.pip_install(venv_path, requirements_file)

                    script = os.path.join(_tools_dir(), 'modify_master.py')
                    configure_cmd = ['python', script]
                    venvutil.run_cmd(venv_path, configure_cmd)
                else:
                    venvutil.create_venv(venv_path)
                    venvutil.pip_install(venv_path, requirements_file)
                    venvutil.activate_venv(venv_path)

                    # import delayed until dependencies exist
                    import modify_master
                    modify_master.set_local_infinity_defaults()
        finally:
            os.environ.clear()
            os.environ.update(saved_env)

    def apply_default_config(self):
        self.create_service_account()
        self.configure_master_settings()

# TODO: figure out how to determine all the necessary values from
# CLUSTER_URL etc
