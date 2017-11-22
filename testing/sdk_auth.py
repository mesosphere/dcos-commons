'''
Utilities related to setting up a Kerberos environment for services to test authentication
and authorization functionality.

Note: This module assumes any package it's being tested with includes the relevant
krb5.conf and/or JAAS file(s) as artifacts, specified as per the YAML service spec.

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_auth IN ANY OTHER PARTNER REPOS
************************************************************************
'''
from retrying import retry
from subprocess import run
from tempfile import TemporaryDirectory

import dcos
import json
import logging
import os
import shakedown

import sdk_cmd
import sdk_hosts
import sdk_marathon
import sdk_tasks
import sdk_security


log = logging.getLogger(__name__)

KERBEROS_APP_ID = "kdc"
KERBEROS_IMAGE_NAME = "mesosphere/kdc"
KERBEROS_KEYTAB_FILE_NAME = "keytab"
BASE64_ENCODED_KEYTAB_FILE_NAME = "{keytab_name}.base64"
DCOS_BASE64_PREFIX = "__dcos_base64__"
LINUX_USER = "core"
KERBEROS_CONF = "krb5.conf"
REALM = "LOCAL"

# Note: Some of the helper functions in this module are wrapped in basic retry logic to provide some
# resiliency towards possible intermittent network failures.


@retry(stop_max_attempt_number=3, wait_fixed=2000)
def _get_kdc_task() -> dict:
    """
    :return (dict): The task object of the KDC app with desired properties to be retrieved by other methods.
    """
    log.info("Getting KDC task")
    raw_tasks = sdk_cmd.run_cli("task --json")
    if raw_tasks:
        tasks = json.loads(raw_tasks)
        for task in tasks:
            if task["name"] == KERBEROS_APP_ID:
                return task

    raise RuntimeError("Expecting marathon KDC task but no such task found. Running tasks: {tasks}".format(
        tasks=raw_tasks))


@retry(stop_max_attempt_number=2, wait_fixed=1000)
def _get_host_name(host_id: str) -> str:
    """
    Fetches the host name for the host running the KDC app.
    :param host_id (str): The ID of the host, used to look up the appropriate node.
    :return (str): Name of the host running the KDC app.
    """
    log.info("Getting host name")
    raw_nodes = sdk_cmd.run_cli("node --json")
    if raw_nodes:
        nodes = json.loads(raw_nodes)
        for node in nodes:
            if node["id"] == host_id:
                log.info("Host name is {host_name}".format(host_name=node["hostname"]))
                return node["hostname"]

    raise RuntimeError("Failed to get name of host running the KDC app: {nodes}")


@retry(stop_max_attempt_number=2, wait_fixed=1000)
def _get_master_public_ip() -> str:
    """
    :return (str): The public IP of the master node in the DC/OS cluster.
    """
    dcos_url, headers = sdk_security.get_dcos_credentials()
    cluster_metadata_url = "{cluster_url}/metadata".format(cluster_url=dcos_url)
    response = sdk_cmd.request("GET", cluster_metadata_url, verify=False)
    if not response.ok:
        raise RuntimeError("Unable to get the master node's public IP address: {err}".format(err=repr(response)))

    response = response.json()
    if "PUBLIC_IPV4" not in response:
        raise KeyError("Cluster metadata does not include master's public ip: {response}".format(
            response=repr(response)))

    public_ip = response["PUBLIC_IPV4"]
    log.info("Master public ip is {public_ip}".format(public_ip=public_ip))
    return public_ip


def _create_temp_working_dir() -> TemporaryDirectory:
    """
    Creates a temporary working directory to enable setup of the Kerberos environment.
    :return (TemporaryDirectory): The TemporaryDirectory object holding the context of the temp dir.
    """
    tmp_dir = TemporaryDirectory()
    log.info("Created temp working directory {}".format(tmp_dir.name))
    return tmp_dir


#TODO: make this generic and put in sdk_utils.py
def _copy_file_to_localhost(self):
    """
    Copies the keytab that was generated inside the container running the KDC server to the localhost
    so it can be uploaded to the secret store later.

    The keytab will end up in path: <temp_working_dir>/<keytab_file>
    """
    log.info("Copying {} to the temp working directory".format(self.keytab_file_name))

    keytab_absolute_path = "{mesos_agents_path}/{host_id}/frameworks/{framework_id}/executors/{task_id}/runs/latest/{keytab_file}".format(
        mesos_agents_path="/var/lib/mesos/slave/slaves",
        host_id=self.kdc_host_id,
        framework_id=self.framework_id,
        task_id=self.task_id,
        keytab_file=self.keytab_file_name
    )
    keytab_url = "{cluster_url}/slave/{agent_id}/files/download?path={path}".format(
        cluster_url=shakedown.dcos_url(),
        agent_id=self.kdc_host_id,
        path=keytab_absolute_path
    )
    dest = "{temp_working_dir}/{keytab_file}".format(
        temp_working_dir=self.temp_working_dir.name, keytab_file=self.keytab_file_name)

    curl_cmd = "curl -k --header '{auth}' {url} > {dest_file}".format(
        auth="Authorization: token={token}".format(token=shakedown.dcos_acs_token()),
        url=keytab_url,
        dest_file=dest
    )
    try:
        run([curl_cmd], shell=True)
    except Exception as e:
        raise RuntimeError("Failed to download the keytab file: {}".format(repr(e)))


def kinit(task_id: str, keytab: str, principal:str):
    """
    Performs a kinit command to authenticate the specified principal.
    :param task_id: The task in whose environment the kinit will run.
    :param keytab: The keytab used by kinit to authenticate.
    :param principal: The name of the principal the user wants to authenticate as.
    """
    kinit_cmd = "kinit -k -t {keytab} {principal}".format(keytab=keytab, principal=principal)
    sdk_tasks.task_exec(task_id, kinit_cmd)


class KerberosEnvironment:
    def __init__(self):
        """
        Installs the Kerberos Domain Controller (KDC) as the initial step in creating a kerberized cluster.
        This just passes a dictionary to be rendered as a JSON app defefinition to marathon.
        """
        self.temp_working_dir = _create_temp_working_dir()
        kdc_app_def_path = "{current_file_dir}/../tools/kdc.json".format(
            current_file_dir=os.path.dirname(os.path.realpath(__file__)))
        with open(kdc_app_def_path) as f:
            kdc_app_def = json.load(f)

        kdc_app_def["id"] = KERBEROS_APP_ID
        sdk_marathon.install_app(kdc_app_def)
        self.kdc_port = int(kdc_app_def["portDefinitions"][0]["port"])
        self.kdc_host = "{app_name}.{service_name}.{autoip_host_suffix}".format(
                app_name=KERBEROS_APP_ID, service_name="marathon", autoip_host_suffix=sdk_hosts.AUTOIP_HOST_SUFFIX)
        self.kdc_realm = REALM
        self.kdc_task = _get_kdc_task()
        self.framework_id = self.kdc_task["framework_id"]
        self.task_id = self.kdc_task["id"]
        self.kdc_host_id = self.kdc_task["slave_id"]
        self.kdc_host_name = _get_host_name(self.kdc_host_id)
        self.master_public_ip = _get_master_public_ip()
        self.principals = []
        self.keytab_file_name = KERBEROS_KEYTAB_FILE_NAME
        self.base64_encoded_keytab_file_name = BASE64_ENCODED_KEYTAB_FILE_NAME.format(keytab_name=self.keytab_file_name)

        # For secret creation/deletion
        cmd = "package install --yes --cli dcos-enterprise-cli"
        try:
            sdk_cmd.run_cli(cmd)
        except dcos.errors.DCOSException as e:
            raise RuntimeError("Failed to install the dcos-enterprise-cli: {}".format(repr(e)))

    def __run_kadmin(self, options: list, cmd: str, args: list):
        """
        Invokes Kerberos' kadmin binary inside the container to run some command.
        :param options (list): A list of options given to kadmin.
        :param cmd (str): The name of the sub command to run.
        :param args (list): A list of arguments passed to the sub command. This should also include any flags
                            needed to be set for the sub command.
        :raises a generic Exception if the invocation fails.
        """
        kadmin_cmd = "/usr/sbin/kadmin {options} {cmd} {args}".format(
            options=' '.join(options),
            cmd=cmd,
            args=' '.join(args)
        )
        log.info("Running kadmin: {}".format(kadmin_cmd))
        try:
            sdk_tasks.task_exec(self.task_id, kadmin_cmd)
        except Exception as e:
            log.error("Failed to run kadmin: {}".format(repr(e)))
            raise e

    def add_principals(self, principals: list):
        """
        Adds a list of principals to the KDC. A principal is defined as a concatenation of 3 parts
        in the following order:
        - primary: first part of the principal. In the case of a user, it's the same as your username.
                   For a host, the primary is the word host.
        - instance: The instance is a string that qualifies the primary.
                    In the case of a user, the instance is usually null, but a user might also have an additional
                    principal, with an instance called admin.  In the case of a host, the instance is the fully
                    qualified hostname, e.g., daffodil.mit.edu.
        - realm: your Kerberos realm. In most cases, your Kerberos realm is your domain name, in upper-case letters.

        More info on principal definition:
        https://web.mit.edu/kerberos/krb5-1.5/krb5-1.5.4/doc/krb5-user/What-is-a-Kerberos-Principal_003f.html

        A principal is formatted as: <primary>/instance@realm
        Eg. hdfs/name-0-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL

        :param principals: The list of principals to be added to KDC.
        """
        # TODO: Perform sanitation check against validity of format for all given principals and raise an
        # exception when the format of a principal is invalid.
        self.principals = list(map(lambda x: x.strip(), principals))

        log.info("Adding the following list of principals to the KDC: {principals}".format(principals=self.principals))
        kadmin_options = ["-l"]
        kadmin_cmd = "add"
        kadmin_args = ["--use-defaults", "--random-password"]

        try:
            kadmin_args.extend(self.principals)
            self.__run_kadmin(kadmin_options, kadmin_cmd, kadmin_args)
        except Exception as e:
            raise RuntimeError("Failed to add principals {principals}: {err_msg}".format(
                principals=self.principals, err_msg=repr(e)))

        log.info("Principals successfully added to KDC")

    def __create_and_fetch_keytab(self):
        """
        Creates the keytab file that holds the info about all the principals that have been
        added to the KDC. It also fetches it locally so that later the keytab can be uploaded to the secret store.
        """
        log.info("Creating the keytab")
        kadmin_options = ["-l"]
        kadmin_cmd = "ext"
        kadmin_args = ["-k", self.keytab_file_name] + self.principals
        self.__run_kadmin(kadmin_options, kadmin_cmd, kadmin_args)

        _copy_file_to_localhost(self)

    def __create_and_upload_secret(self):
        """
        This method base64 encodes the keytab file and creates a secret with this encoded content so the
        tasks can fetch it.
        """
        log.info("Creating and uploading the keytab file to the secret store")

        try:
            base64_encode_cmd = "base64 -w 0 {source} > {destination}".format(
                source=os.path.join(self.temp_working_dir.name, self.keytab_file_name),
                destination=os.path.join(self.temp_working_dir.name, self.base64_encoded_keytab_file_name)
            )
            run(base64_encode_cmd, shell=True)
        except Exception as e:
            raise Exception("Failed to base64-encode the keytab file: {}".format(repr(e)))

        self.keytab_secret_path = "{}_keytab".format(DCOS_BASE64_PREFIX)

        # TODO: check if a keytab secret of same name already exists
        create_secret_cmd = "security secrets create {keytab_secret_path} --value-file {encoded_keytab_path}".format(
            keytab_secret_path=self.keytab_secret_path,
            encoded_keytab_path=os.path.join(self.temp_working_dir.name, self.base64_encoded_keytab_file_name)
        )
        try:
            sdk_cmd.run_cli(create_secret_cmd)
        except RuntimeError as e:
            raise RuntimeError("Failed to create secret for the base64-encoded keytab file: {}".format(repr(e)))

        log.info("Successfully uploaded a base64-encoded keytab file to the secret store")

    def finalize(self):
        """
        Once the principals have been added, the rest of the environment setup does not ask for more info and can be
        automated, hence this method.
        """
        self.__create_and_fetch_keytab()
        self.__create_and_upload_secret()

    def get_host(self):
        return self.kdc_host

    def get_port(self):
        return str(self.kdc_port)

    def get_keytab_path(self):
        return self.keytab_secret_path

    def get_realm(self):
        return self.kdc_realm

    def get_kdc_address(self):
        return "{host}:{port}".format(host=self.kdc_host, port=self.kdc_port)

    def cleanup(self):
        log.info("Removing the marathon KDC app")
        sdk_marathon.destroy_app(KERBEROS_APP_ID)

        log.info("Deleting temporary working directory")
        self.temp_working_dir.cleanup()

        #TODO: separate secrets handling into another module
        log.info("Deleting keytab secret")
        sdk_security.delete_secret(self.keytab_secret_path)
