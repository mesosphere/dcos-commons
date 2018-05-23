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
import retrying
import subprocess

import sdk_cmd
import sdk_hosts
import sdk_marathon
import sdk_security


log = logging.getLogger(__name__)

KERBEROS_APP_ID = "kdc"
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
    raw_tasks = sdk_cmd.run_cli("task --json", print_output=False)
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
    response = sdk_cmd.cluster_request("GET", "/metadata", verify=False).json()
    if "PUBLIC_IPV4" not in response:
        raise KeyError("Cluster metadata does not include master's public ip: {response}".format(
            response=response))

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
    log.info("Downloading keytab to %s", output_filename)

    keytab_response = sdk_cmd.cluster_request(
        'GET', "/slave/{}/files/download".format(host_id), params={"path": keytab_absolute_path})
    with open(output_filename, 'wb') as fd:
        for chunk in keytab_response.iter_content(chunk_size=128):
            fd.write(chunk)

    log.info("Downloaded %d bytes to %s", os.stat(output_filename).st_size, output_filename)


def kinit(marathon_task_id: str, keytab: str, principal: str):
    """
    Performs a kinit command to authenticate the specified principal.
    :param marathon_task_id: The marathon task in whose environment the kinit will run.
    :param keytab: The keytab used by kinit to authenticate.
    :param principal: The name of the principal the user wants to authenticate as.
    """
    kinit_cmd = "kinit -kt {keytab} {principal}".format(keytab=keytab, principal=principal)
    log.info("Authenticating principal=%s with keytab=%s: %s", principal, keytab, kinit_cmd)
    rc, stdout, stderr = sdk_cmd.marathon_task_exec(marathon_task_id, kinit_cmd)
    if rc != 0:
        raise RuntimeError("Failed ({}) to authenticate with keytab={} principal={}\n"
                           "stdout: {}\n"
                           "stderr: {}".format(rc, keytab, principal, stdout, stderr))


def kdestroy(marathon_task_id: str):
    """
    Performs a kdestroy command to erase an auth session for a principal.
    :param task_id: The marathon task in whose environment the kdestroy will run.
    """
    log.info("Erasing auth session:")
    rc, stdout, stderr = sdk_cmd.marathon_task_exec(marathon_task_id, "kdestroy")
    if rc != 0:
        raise RuntimeError("Failed ({}) to erase auth session\nstdout: {}\nstderr: {}".format(rc, stdout, stderr))


class KerberosEnvironment:
    def __init__(self, persist: bool=False):
        """
        Installs the Kerberos Domain Controller (KDC) as the initial step in creating a kerberized cluster.
        This just passes a dictionary to be rendered as a JSON app definition to marathon.
        """
        self._persist = persist
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
        self.kdc_realm = REALM

        self.set_keytab_path("_keytab", is_binary=False)

    def load_kdc_app_definition(self) -> dict:
        kdc_app_def_path = "{current_file_dir}/../tools/kdc/kdc.json".format(
            current_file_dir=os.path.dirname(os.path.realpath(__file__)))
        with open(kdc_app_def_path) as fd:
            kdc_app_def = json.load(fd)

        kdc_app_def["id"] = self.app_id

        return kdc_app_def

    def install(self) -> dict:

        @retrying.retry(stop_max_delay=3*60*1000,
                        wait_exponential_multiplier=1000,
                        wait_exponential_max=120 * 1000,
                        retry_on_result=lambda result: not result)
        def _install_marathon_app(app_definition):
            success, _ = sdk_marathon.install_app(app_definition)
            return success

        if sdk_marathon.app_exists(self.app_definition["id"]):
            if self._persist:
                log.info("Found installed KDC app, reusing it")
                return _get_kdc_task(self.app_definition["id"])
            log.info("Found installed KDC app, destroying it first")
            sdk_marathon.destroy(self.app_definition["id"])

        log.info("Installing KDC Marathon app")
        _install_marathon_app(self.app_definition)
        log.info("KDC app installed successfully")

        return _get_kdc_task(self.app_definition["id"])

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
        rc, stdout, stderr = sdk_cmd.marathon_task_exec(self.task_id, kadmin_cmd)
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

    def create_remote_keytab(self, keytab_id: str, principals: list=[]) -> str:
        """
        Create a remote keytab for the specified list of principals
        """
        name = "{}.{}.keytab".format(keytab_id, str(uuid.uuid4()))

        log.info("Creating keytab: %s", name)

        if not principals:
            log.info("Using predefined principals")
            principals = self.principals

        if not principals:
            log.error("No principals specified not creating keytab")
            return None

        log.info("Deleting any previous keytab just in case (kadmin will append to it)")
        sdk_cmd.marathon_task_exec(self.task_id, "rm {}".format(name))

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

    @retrying.retry(stop_max_attempt_number=2, wait_fixed=5000)
    def get_keytab_for_principals(self, keytab_id: str, principals: list):
        """
        Download a generated keytab for the specified list of principals
        """
        remote_keytab_path = self.create_remote_keytab(keytab_id, principals=principals)
        local_keytab_path = self.get_working_file_path(os.path.basename(remote_keytab_path))

        _copy_file_to_localhost(self.kdc_host_id, remote_keytab_path, local_keytab_path)

        # In a fun twist, at least in the HDFS tests, we sometimes wind up with a _bad_ keytab. I know, right?
        # We can validate if it is good or bad by checking it with some internal Java APIs.
        #
        # See HDFS-493 if you'd like to learn more. Personally, I'd like to forget about this.
        command = "java -jar {} {}".format(
            os.path.join(os.path.dirname(os.path.realpath(__file__)),
                         "security",
                         "keytab-validator",
                         "keytab-validator.jar"),
            local_keytab_path)
        result = subprocess.run(command,
                                shell=True,
                                stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE)

        log.info(result.stdout)
        if result.returncode is not 0:
            # reverse the principal list before generating again.
            principals.reverse()
            raise Exception("The keytab is bad :(. "
                            "We're going to retry generating this keytab with reversed principals. "
                            "What fun.")

        return local_keytab_path

    def __create_and_fetch_keytab(self):
        """
        Creates the keytab file that holds the info about all the principals
        that have been added to the KDC. It also fetches it locally so that
        the keytab can be uploaded to the secret store later.
        """
        keytab_id = self.get_keytab_path().replace("/", "_")
        return self.get_keytab_for_principals(keytab_id, self.principals)

    def __encode_secret(self, keytab_path: str) -> str:
        if self.get_keytab_path().startswith(DCOS_BASE64_PREFIX):
            try:
                base64_encoded_keytab_path = "{}.base64".format(keytab_path)
                with open(keytab_path, "rb") as f:
                    keytab = f.read()

                base64_encoding = base64.b64encode(keytab).decode("utf-8")
                with open(base64_encoded_keytab_path, "w") as f:
                    f.write(base64_encoding)

                log.info("Finished base64-encoding secret content (%d bytes): %s", len(base64_encoding), base64_encoding)

                return ["--value-file", base64_encoded_keytab_path, ]
            except Exception as e:
                raise Exception("Failed to base64-encode the keytab file: {}".format(repr(e)))

        log.info("Creating binary secret from %s", keytab_path)
        return ["-f", keytab_path]

    def __create_and_upload_secret(self, keytab_path: str):
        """
        This method base64 encodes the keytab file and creates a secret with this encoded content so the
        tasks can fetch it.
        """
        log.info("Creating and uploading the keytab file %s to the secret store", keytab_path)

        encoding_options = self.__encode_secret(keytab_path)

        sdk_security.install_enterprise_cli()
        # try to delete any preexisting secret data:
        sdk_security.delete_secret(self.keytab_secret_path)
        # create new secret:

        cmd_list = ["security",
                    "secrets",
                    "create",
                    self.get_keytab_path(),
                    ]
        cmd_list.extend(encoding_options)

        create_secret_cmd = " ".join(cmd_list)
        log.info("Creating secret %s: %s", self.get_keytab_path(), create_secret_cmd)
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

    def get_keytab_path(self) -> str:
        return self.keytab_secret_path

    def set_keytab_path(self, secret_path: str, is_binary: bool):
        if is_binary:
            prefix = ""
        else:
            prefix = DCOS_BASE64_PREFIX

        self.keytab_secret_path = "{}{}".format(prefix, secret_path)

    def get_realm(self) -> str:
        return self.kdc_realm

    def get_kdc_address(self):
        return ":".join(str(p) for p in [self.get_host(), self.get_port()])

    def get_principal(self, primary: str, instance: str=None) -> str:

        if instance:
            principal = "{}/{}".format(primary, instance)
        else:
            principal = primary

        return "{}@{}".format(principal, self.get_realm())

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
