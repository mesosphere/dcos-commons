#!/usr/bin/env python3

import argparse
import logging
import os
import os.path
import shutil
import subprocess
import sys
import tempfile

import cli_install
import dcos_login

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
    def __init__(self, aws_stack_id, auth_token, dns_address,
            is_enterprise=False, security=None):
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
        logger.info(fmt.format(self.dns_address, self.aws_stack_id))
        import enable_mount_volumes
        # fabric spams to stdout, which causes problems with launch_ccm_cluster.
        # force total redirect to stderr:
        enable_mount_volumes.main(self.aws_stack_id, stdout=sys.stderr)

    def _install_cli(self):
        # create_service_account relies on dcos cli, which we may not have
        # at this point.
        self.cli_tempdir = tempfile.mkdtemp(prefix="conf_cluster")
        cli_install.download_cli(self.dcos_url, self.cli_tempdir)

    def _run_shellscript_with_cli(self, script, args):
        custom_env = os.environ.copy()
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
        logger.info(fmt.format(self.dns_address, self.aws_stack_id))

        self._run_shellscript_with_cli('create_service_account.sh', [self.dcos_url, self.auth_token, '--strict'])

#   def setup_roles(self):
        # Examples of what individual tests should run. See respective projects' "test.sh":
        #_run_script('setup_permissions.sh', 'nobody cassandra-role'.split())
        #_run_script('setup_permissions.sh', 'nobody hdfs-role'.split())
        #_run_script('setup_permissions.sh', 'nobody kafka-role'.split())

    def _initialize_dcos_cli(self):
        logger.info("Initializing dcos config")
        subprocess.check_call(['which', 'dcos'])
        subprocess.check_call(['dcos', 'config', 'set', 'core.dcos_url', self.dcos_url])
        subprocess.check_call(['dcos', 'config', 'set', 'core.reporting', 'True'])
        subprocess.check_call(['dcos', 'config', 'set', 'core.ssl_verify', 'False'])
        subprocess.check_call(['dcos', 'config', 'set', 'core.timeout', '5'])
        subprocess.check_call(['dcos', 'config', 'show'])
        dcos_login.DCOSLogin(self.dcos_url).login()

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
                    if initmaster and self.security != 'strict':
                        # XXX: modify_master is hanging in strict mode:
                        # INFINITY-1439; remove second half of boolean once
                        # solved.

                        # currently, the create_service_account.sh script sets up the
                        # cli itself so we initialize it in the style that test logic
                        # expects after.
                        # in the shiny future, set up the CLI once for the whole run.
                        self._initialize_dcos_cli()
                finally:
                    sys.stdout.flush()
                    os.dup2(stdout_back, stdout_fd)
        finally:
            os.environ.clear()
            os.environ.update(saved_env)

# TODO: figure out how to determine all the necessary values from
# CLUSTER_URL etc

def handle_args():
    parser = argparse.ArgumentParser(description="Configure a cluster for running our tests")
    parser.add_argument("aws_stack_id",
        help="amazon stack id (only used when creating mount volumes")
    parser.add_argument("auth_token",
        help="dcos auth token")
    parser.add_argument("cluster_address",
        help="hostname or ip address of cluster")
    parser.add_argument("--security-mode",
            choices=('strict', 'permissive', 'disabled'),
            default=os.environ.get("SECURITY") or 'permissive',
            help="Security mode of cluster, defaults to permissive")
    parser.add_argument("--config-master", action='store_true',
            default=False,
            help="configure the mesos master for some tests")
    parser.add_argument("--config-mount-volumes", action='store_true',
            default=False,
            help="configure mount volumes on agents")
    args = parser.parse_args()
    if args.security_mode == "disabled":
        args.security_mode = None
    return args

if __name__ == "__main__":
    args = handle_args()

    # assume enterprise for now
    is_enterprise = True
    clustinit = ClusterInitializer(args.aws_stack_id, args.auth_token,
            args.cluster_address, is_enterprise, args.security_mode)
    clustinit.apply_default_config(initmaster=args.config_master)

    if args.config_mount_volumes:
        clustinit.create_mount_volumes
