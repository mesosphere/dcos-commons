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
import tempfile

import base64
import json
import logging
import os
import uuid
import requests
import retrying

import sdk_cmd
import sdk_hosts
import sdk_marathon
import sdk_tasks
import sdk_security


log = logging.getLogger(__name__)

KERBEROS_APP_ID = "kdc"
KERBEROS_KEYTAB_FILE_NAME = "keytab"
DCOS_BASE64_PREFIX = "__dcos_base64__"
LINUX_USER = "core"
KERBEROS_CONF = "krb5.conf"
REALM = "LOCAL"

# Note: Some of the helper functions in this module are wrapped in basic retry logic to provide some
# resiliency towards possible intermittent network failures.


@retrying.retry(stop_max_attempt_number=3, wait_fixed=2000)
def _get_kdc_task(task_name: str) -> dict:
    """
    :return (dict): The task object of the KDC app with desired properties to be retrieved by other methods.
    """
    log.info("Getting KDC task")
    raw_tasks = sdk_cmd.run_cli("task --json")
    if raw_tasks:
        tasks = json.loads(raw_tasks)
        for task in tasks:
            if task["name"] == task_name:
                return task

    raise RuntimeError("Expecting marathon KDC task but no such task found. Running tasks: {tasks}".format(
        tasks=raw_tasks))


@retrying.retry(stop_max_attempt_number=2, wait_fixed=1000)
def _get_host_name(host_id: str) -> str:
    """
    Fetches the host name for the host running the KDC app.
    :param host_id (str): The ID of the host, used to look up the appropriate node.
    :return (str): Name of the host running the KDC app.
    """
    log.info("Getting hostname")
    raw_nodes = sdk_cmd.run_cli("node --json")
    if raw_nodes:
        nodes = json.loads(raw_nodes)
        for node in nodes:
            if "id" in node and node["id"] == host_id:
                log.info("Host name is %s", node["hostname"])
                return node["hostname"]

    raise RuntimeError("Failed to get name of host running the KDC app: {nodes}")


@retrying.retry(stop_max_attempt_number=2, wait_fixed=1000)
def _get_master_public_ip() -> str:
    """
    :return (str): The public IP of the master node in the DC/OS cluster.
    """
    dcos_url, _ = sdk_security.get_dcos_credentials()
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


def _create_temp_working_dir() -> tempfile.TemporaryDirectory:
    """
    Creates a temporary working directory to enable setup of the Kerberos environment.
    :return (tempfile.TemporaryDirectory): The tempfile.TemporaryDirectory object holding the context of the temp dir.
    """
    tmp_dir = tempfile.TemporaryDirectory()
    log.info("Created temp working directory {}".format(tmp_dir.name))
    return tmp_dir


# TODO: make this generic and put in sdk_utils.py
def _copy_file_to_localhost(host_id: str, keytab_absolute_path: str, output_filename: str):
    """
    Copies the keytab that was generated inside the container running the KDC server to the localhost
    so it can be uploaded to the secret store later.
    """
    dcos_url, headers = sdk_security.get_dcos_credentials()
    del headers["Content-Type"]

    keytab_url = "{cluster_url}/slave/{agent_id}/files/download?path={path}".format(
        cluster_url=dcos_url,
        agent_id=host_id,
        path=keytab_absolute_path
    )

    log.info("Downloading keytab %s to %s", keytab_url, output_filename)

    @retrying.retry(wait_exponential_multiplier=1000,
                    wait_exponential_max=120 * 1000,
                    retry_on_exception=lambda e: isinstance(e, requests.exceptions.HTTPError),
                    wrap_exception=True)
    def get_download_stream(url: str) -> requests.Response:
        """ Use a streaming call to GET to download the Keytab file """
        response = requests.get(url, headers=headers, stream=True, verify=False)
        try:
            response.raise_for_status()
        except requests.exceptions.HTTPError as e:
            log.error("Error fetching file from %s", url)
            log.error("response=%s exception=%s", response, repr(e))
            raise e

        return response

    try:
        log.info("Downloading keytab from %s", keytab_url)
        keytab_response = get_download_stream(keytab_url)
        with open(output_filename, 'wb') as fd:
            for chunk in keytab_response.iter_content(chunk_size=128):
                fd.write(chunk)
    except retrying.RetryError as e:
        log.error("%s", e)
        raise RuntimeError("Failed to download the keytab file: {}".format(repr(e)))

    log.info("Downloaded %d bytes from %s to %s", os.stat(output_filename).st_size, keytab_url, output_filename)


def kinit(task_id: str, keytab: str, principal: str):
    """
    Performs a kinit command to authenticate the specified principal.
    :param task_id: The task in whose environment the kinit will run.
    :param keytab: The keytab used by kinit to authenticate.
    :param principal: The name of the principal the user wants to authenticate as.
    """
    kinit_cmd = "kinit -kt {keytab} {principal}".format(keytab=keytab, principal=principal)
    log.info("Authenticating principal=%s with keytab=%s: %s", principal, keytab, kinit_cmd)
    rc, stdout, stderr = sdk_tasks.task_exec(task_id, kinit_cmd)
    if rc != 0:
        raise RuntimeError("Failed ({}) to authenticate with keytab={} principal={}\nstdout: {}\nstderr: {}".format(rc, keytab, principal, stdout, stderr))


def kdestroy(task_id: str):
    """
    Performs a kdestroy command to erase an auth session for a principal.
    :param task_id: The task in whose environment the kinit will run.
    """
    log.info("Erasing auth session:")
    rc, stdout, stderr = sdk_tasks.task_exec(task_id, "kdestroy")
    if rc != 0:
        raise RuntimeError("Failed ({}) to erase auth session\nstdout: {}\nstderr: {}".format(rc, stdout, stderr))


class KerberosEnvironment:
    def __init__(self):
        """
        Installs the Kerberos Domain Controller (KDC) as the initial step in creating a kerberized cluster.
        This just passes a dictionary to be rendered as a JSON app definition to marathon.
        """
        self._working_dir = None
        self._temp_working_dir = None

        # Application settings
        self.app_id = KERBEROS_APP_ID
        self.app_definition = self.load_kdc_app_definition()

        # TODO: Ideally, we should not install this service in the constructor.
        kdc_task_info = self.install()

        # Running task information
        self.framework_id = kdc_task_info["framework_id"]
        self.task_id = kdc_task_info["id"]
        self.kdc_host_id = kdc_task_info["slave_id"]

        # Kerberos-specific information
        self.principals = []
        self.keytab_file_name = KERBEROS_KEYTAB_FILE_NAME
        self.kdc_realm = REALM

    def load_kdc_app_definition(self) -> dict:
        kdc_app_def_path = "{current_file_dir}/../tools/kdc/kdc.json".format(
            current_file_dir=os.path.dirname(os.path.realpath(__file__)))
        with open(kdc_app_def_path) as fd:
            kdc_app_def = json.load(fd)

        kdc_app_def["id"] = self.app_id

        return kdc_app_def

    def install(self) -> dict:

        @retrying.retry(wait_exponential_multiplier=1000,
                        wait_exponential_max=120 * 1000,
                        retry_on_result=lambda result: not result)
        def _install_marathon_app(app_definition):
            success, _ = sdk_marathon.install_app(app_definition)
            return success

        _install_marathon_app(self.app_definition)
        log.info("KDC app installed successfully")

        kdc_task_info = _get_kdc_task(self.app_definition["id"])

        return kdc_task_info

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
        rc, stdout, stderr = sdk_tasks.task_exec(self.task_id, kadmin_cmd)
        if rc != 0:
            raise RuntimeError("Failed ({}) to invoke kadmin: {}\nstdout: {}\nstderr: {}".format(rc, kadmin_cmd, stdout, stderr))

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


    def create_remote_keytab(self, name: str, principals: list=[]) -> str:
        """
        Create a remote keytab for the specified list of principals
        """
        if not name:
            name = "{}.keytab".format(str(uuid.uuid4()))

        log.info("Creating keytab: %s", name)

        if not principals:
            log.info("Using predefined principals")
            principals = self.principals

        if not principals:
            log.error("No principals specified not creating keytab")
            return None

        kadmin_options = ["-l"]
        kadmin_cmd = "ext"
        kadmin_args = ["-k", name]
        kadmin_args.extend(principals)

        self.__run_kadmin(kadmin_options, kadmin_cmd, kadmin_args)

        keytab_absolute_path = os.path.join("/var/lib/mesos/slave/slaves", self.kdc_host_id,
                                            "frameworks", self.framework_id,
                                            "executors", self.task_id,
                                            "runs/latest", name)
        return keytab_absolute_path

    def get_keytab_for_principals(self, principals: list, output_filename: str):
        """
        Download a generated keytab for the specified list of principals
        """
        remote_keytab_path = self.create_remote_keytab(self.keytab_file_name, principals=principals)
        _copy_file_to_localhost(self.kdc_host_id, remote_keytab_path, output_filename)

    def __create_and_fetch_keytab(self):
        """
        Creates the keytab file that holds the info about all the principals that have been
        added to the KDC. It also fetches it locally so that later the keytab can be uploaded to the secret store.
        """
        local_keytab_filename = self.get_working_file_path(self.keytab_file_name)
        self.get_keytab_for_principals(self.principals, local_keytab_filename)

        return local_keytab_filename

    def __create_and_upload_secret(self, keytab_path: str):
        """
        This method base64 encodes the keytab file and creates a secret with this encoded content so the
        tasks can fetch it.
        """
        log.info("Creating and uploading the keytab file %s to the secret store", keytab_path)

        try:
            base64_encoded_keytab_path = "{}.base64".format(keytab_path)
            with open(keytab_path, "rb") as f:
                keytab = f.read()

            base64_encoding = base64.b64encode(keytab).decode("utf-8")
            with open(base64_encoded_keytab_path, "w") as f:
                f.write(base64_encoding)

            log.info("Finished base64-encoding secret content (%d bytes): %s", len(base64_encoding), base64_encoding)

        except Exception as e:
            raise Exception("Failed to base64-encode the keytab file: {}".format(repr(e)))

        self.keytab_secret_path = "{}_keytab".format(DCOS_BASE64_PREFIX)

        sdk_security.install_enterprise_cli()
        # try to delete any preexisting secret data:
        sdk_security.delete_secret(self.keytab_secret_path)
        # create new secret:
        create_secret_cmd = "security secrets create {keytab_secret_path} --value-file {encoded_keytab_path}".format(
            keytab_secret_path=self.keytab_secret_path,
            encoded_keytab_path=base64_encoded_keytab_path)
        log.info("Creating secret named %s from file %s: %s", self.keytab_secret_path, base64_encoded_keytab_path, create_secret_cmd)
        rc, stdout, stderr = sdk_cmd.run_raw_cli(create_secret_cmd)
        if rc != 0:
            raise RuntimeError("Failed ({}) to create secret: {}\nstdout: {}\nstderr: {}".format(rc, create_secret_cmd, stdout, stderr))

        log.info("Successfully uploaded a base64-encoded keytab file to the secret store")

    def finalize(self):
        """
        Once the principals have been added, the rest of the environment setup does not ask for more info and can be
        automated, hence this method.
        """
        local_keytab_path = self.__create_and_fetch_keytab()
        self.__create_and_upload_secret(local_keytab_path)

    def get_working_file_path(self, *args):
        if not self._working_dir:
            if not self._temp_working_dir:
                self._temp_working_dir = _create_temp_working_dir()
            self._working_dir = self._temp_working_dir.name

        working_filepath = os.path.join(self._working_dir, *args)
        return working_filepath

    def get_host(self):
        return sdk_hosts.autoip_host(service_name="marathon", task_name=self.app_definition["id"])

    def get_port(self):
        return str(self.app_definition["portDefinitions"][0]["port"])

    def get_keytab_path(self):
        return self.keytab_secret_path

    def get_realm(self):
        return self.kdc_realm

    def get_kdc_address(self):
        return ":".join([self.get_host(), self.get_port()])

    def cleanup(self):
        sdk_security.install_enterprise_cli()

        log.info("Removing the marathon KDC app")
        sdk_marathon.destroy_app(self.app_definition["id"])

        if self._temp_working_dir and isinstance(self._temp_working_dir, tempfile.TemporaryDirectory):
            log.info("Deleting temporary working directory")
            self._temp_working_dir.cleanup()

        # TODO: separate secrets handling into another module
        log.info("Deleting keytab secret")
        sdk_security.delete_secret(self.keytab_secret_path)
