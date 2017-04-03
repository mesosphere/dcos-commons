#!/usr/bin/python3

import logging
import os
import os.path
import subprocess
import sys

import venvutil

# Things this needs: cluster_id, stack_id (???), auth_token, dns_address,
# strict mode or not

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")

def _tools_dir():
    return os.path.dirname(os.path.realpath(__file__))

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


    def create_mount_volumes(self):
        fmt = 'Enabling mount volumes for cluster {} (stack id {})'
        logger.info(fmt.format(self.cluster_id, self.stack_id))
        import enable_mount_volumes
        # fabric spams to stdout, which causes problems with launch_ccm_cluster.
        # force total redirect to stderr:
        enable_mount_volumes.main(self.stack_id, stdout=sys.stderr)

    def create_service_account(self):
        if self.security != 'strict':
            fmt ="Skipping creation of service account for security mode {}"
            logger.info(fmt.format(self.security))
            return

        fmt = 'Setting up permissions for cluster {} (stack id {})'
        logger.info(fmt.format(self.cluster_id, self.stack_id))

        def run_script(scriptname, args = []):
            logger.info('Command: {} {}'.format(scriptname, ' '.join(args)))
            script_path = os.path.join(_tools_dir(), scriptname)
            # redirect stdout to stderr:
            subprocess.check_call(['bash', script_path] + args, stdout=sys.stderr)

        run_script('create_service_account.sh', [self.dcos_url, self.auth_token, '--strict'])

#   def setup_roles(self):
        # Examples of what individual tests should run. See respective projects' "test.sh":
        #run_script('setup_permissions.sh', 'nobody cassandra-role'.split())
        #run_script('setup_permissions.sh', 'nobody hdfs-role'.split())
        #run_script('setup_permissions.sh', 'nobody kafka-role'.split())
        #run_script('setup_permissions.sh', 'nobody spark-role'.split())

    def configure_master_settings(self):
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

    def apply_default_config(self):
        self.create_service_account()
        self.configure_master_settings()

