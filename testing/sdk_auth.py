"""
Utilities related to setting up a Kerberos environment for services to test authentication
and authorization functionality.

Note: This module assumes any package it's being tested with includes the relevant
krb5.conf and/or JAAS file(s) as artifacts, specified as per the YAML service spec.

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_auth IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import tempfile

import json
import logging
import os
import retrying

from typing import Any, Dict, List, Optional

import sdk_cmd
import sdk_hosts
import sdk_marathon
import sdk_security


log = logging.getLogger(__name__)

KERBEROS_APP_ID = os.getenv("KERBEROS_APP_ID", "kdc")
REALM = os.getenv("REALM", "LOCAL")
KDC_SERVICE_ACCOUNT = os.getenv("KDC_SERVICE_ACCOUNT", "kdc-admin")
KDC_SERVICE_ACCOUNT_SECRET = os.getenv("KDC_SERVICE_ACCOUNT_SECRET", "kdc-admin")

# Note: Some of the helper functions in this module are wrapped in basic retry logic to provide some
# resiliency towards possible intermittent network failures.


def _get_kdc_task(task_name: str) -> dict:
    """
    :return (dict): The task object of the KDC app with desired properties to be retrieved by other methods.
    """

    @retrying.retry(stop_max_attempt_number=3, wait_fixed=2000)
    def _get_kdc_task_inner(task_name: str) -> dict:
        log.info("Getting KDC task")
        _, raw_tasks, _ = sdk_cmd.run_cli("task --json", print_output=False)
        if raw_tasks:
            tasks = json.loads(raw_tasks)
            for task in tasks:
                assert isinstance(task, dict)
                if task["name"] == task_name:
                    return task

        raise RuntimeError(
            "Expecting marathon KDC task but no such task found. Running tasks: {tasks}".format(
                tasks=raw_tasks
            )
        )

    # we need to convert task_name in case it is foldered to adopt to Marathon conventions
    # e.g. /folder/kdc Marathon App ID becomes kdc.folder Mesos Task ID
    foldered_task_name = ".".join(task_name.split("/")[::-1]).rstrip(".")

    return dict(_get_kdc_task_inner(task_name=foldered_task_name))


@retrying.retry(stop_max_attempt_number=2, wait_fixed=1000)
def _get_host_name(host_id: str) -> str:
    """
    Fetches the host name for the host running the KDC app.
    :param host_id (str): The ID of the host, used to look up the appropriate node.
    :return (str): Name of the host running the KDC app.
    """
    log.info("Getting hostname")
    _, raw_nodes, _ = sdk_cmd.run_cli("node --json")
    if raw_nodes:
        nodes = json.loads(raw_nodes)
        for node in nodes:
            if "id" in node and node["id"] == host_id:
                log.info("Host name is %s", node["hostname"])
                hostname = node["hostname"]
                assert isinstance(hostname, str)
                return hostname

    raise RuntimeError("Failed to get name of host running the KDC app: {nodes}")


@retrying.retry(stop_max_attempt_number=2, wait_fixed=1000)
def _get_master_public_ip() -> str:
    """
    :return (str): The public IP of the master node in the DC/OS cluster.
    """
    response = sdk_cmd.cluster_request("GET", "/metadata").json()
    if "PUBLIC_IPV4" not in response:
        raise KeyError(
            "Cluster metadata does not include master's public ip: {response}".format(
                response=response
            )
        )

    public_ip = response["PUBLIC_IPV4"]
    assert isinstance(public_ip, str)
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
def _copy_file_to_localhost(host_id: str, keytab_absolute_path: str, output_filename: str) -> None:
    """
    Copies the keytab that was generated inside the container running the KDC server to the localhost
    so it can be uploaded to the secret store later.
    """
    log.info("Downloading keytab to %s", output_filename)

    keytab_response = sdk_cmd.cluster_request(
        "GET", "/slave/{}/files/download".format(host_id), params={"path": keytab_absolute_path}
    )
    with open(output_filename, "wb") as fd:
        for chunk in keytab_response.iter_content(chunk_size=128):
            fd.write(chunk)

    log.info("Downloaded %d bytes to %s", os.stat(output_filename).st_size, output_filename)


def kinit(marathon_task_id: str, keytab: str, principal: str) -> None:
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
        raise RuntimeError(
            "Failed ({}) to authenticate with keytab={} principal={}\n"
            "stdout: {}\n"
            "stderr: {}".format(rc, keytab, principal, stdout, stderr)
        )


def kdestroy(marathon_task_id: str) -> None:
    """
    Performs a kdestroy command to erase an auth session for a principal.
    :param task_id: The marathon task in whose environment the kdestroy will run.
    """
    log.info("Erasing auth session:")
    rc, stdout, stderr = sdk_cmd.marathon_task_exec(marathon_task_id, "kdestroy")
    if rc != 0:
        raise RuntimeError(
            "Failed ({}) to erase auth session\nstdout: {}\nstderr: {}".format(rc, stdout, stderr)
        )


class KerberosEnvironment:
    def __init__(self, persist: bool = False):
        """
        Installs the Kerberos Domain Controller (KDC) as the initial step in creating a kerberized cluster.
        This just passes a dictionary to be rendered as a JSON app definition to marathon.
        """
        self._persist = persist
        self._working_dir: Optional[str] = None
        self._temp_working_dir: Optional[tempfile.TemporaryDirectory] = None

        # Application settings
        self.app_id = KERBEROS_APP_ID
        self.app_definition = self.load_kdc_app_definition()

        # TODO: Ideally, we should not install this service in the constructor.
        kdc_task_info = self.install()

        # The target keytab location
        self.keytab_secret_path = None
        self.keytab_secret_binary = False

        # Running task information
        self.framework_id = kdc_task_info["framework_id"]
        self.task_id = kdc_task_info["id"]
        self.kdc_host_id = kdc_task_info["slave_id"]

        # Kerberos-specific information
        self.principals: List[str] = []
        self.kdc_realm = REALM

        self.set_keytab_path("_keytab", is_binary=False)

    def load_kdc_app_definition(self) -> Dict[str, Any]:
        kdc_app_def_path = "{current_file_dir}/../tools/kdc/kdc.json".format(
            current_file_dir=os.path.dirname(os.path.realpath(__file__))
        )
        with open(kdc_app_def_path) as fd:
            kdc_app_def = json.load(fd)

        assert isinstance(kdc_app_def, dict)
        kdc_app_def["id"] = self.app_id

        return kdc_app_def

    def install(self) -> Dict[str, Any]:
        if sdk_marathon.app_exists(self.app_definition["id"]):
            if self._persist:
                log.info("Found installed KDC app, reusing it")
                return _get_kdc_task(self.app_definition["id"])
            log.info("Found installed KDC app, destroying it first")
            sdk_marathon.destroy_app(self.app_definition["id"])

        # (re-)create a service account for the KDC service
        sdk_security.create_service_account(
            service_account_name=KDC_SERVICE_ACCOUNT,
            service_account_secret=KDC_SERVICE_ACCOUNT_SECRET,
        )
        sdk_security._grant(
            KDC_SERVICE_ACCOUNT,
            "dcos:secrets:default:%252F*",
            "Create any secret in the root path",
            "create",
        )
        sdk_security._grant(
            KDC_SERVICE_ACCOUNT,
            "dcos:secrets:default:%252F*",
            "Update any secret in the root path",
            "update",
        )

        log.info("Installing KDC Marathon app")
        sdk_marathon.install_app(self.app_definition)
        log.info("KDC app installed successfully")

        log.info("Waiting for KDC web API endpoint to become available")
        self.__wait_for_kdc_api()
        log.info("KDC web API is now available")

        return _get_kdc_task(self.app_definition["id"])

    def __wait_for_kdc_api(self):
        """
        Keeps polling the KDC Web endpoint until it becomes available
        """

        @retrying.retry(stop_max_attempt_number=30, wait_fixed=2000)
        def probe():
            sdk_cmd.cluster_request(
                "GET", "{}/".format(self.get_service_path()), raise_on_error=True
            )

        return probe()

    def __kdc_api(self, method: str, action: str, json: dict) -> dict:
        """
        Invokes a KDC API command to the remote endpoint
        :param method: 'get' or 'post'
        :param url: The API action to perform
        :param request: The JSON payload to send
        :return (dict):
        :raises a generic Exception if the invocation fails.
        """
        url = "{}/api/{}".format(self.get_service_path(), action)
        log.info("Performing KDC API {method} query to: {url}".format(method=method, url=url))

        return sdk_cmd.cluster_request(
            method, url, headers={"content-type": "application/json"}, json=json
        )

    def list_principals(self, filter: str = "*") -> List[str]:
        """
        Enumerates the principals on the KDC instance that match the given wildcard filter.
        :param filter: the filter expression for the principals to search.
        :raises a generic Exception if the invocation fails.
        """

        res = self.__kdc_api("get", "principals", {"filter": filter})
        if res.status_code != 200:
            raise RuntimeError("Unable to enumerate the principals")

        parsed = res.json()
        print(parsed)

        principals = parsed.get("principals", {})
        return principals.get("list", [])

    def add_principals(self, principals: List[str]) -> None:
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
        newPrincipals = list(map(lambda x: x.strip(), principals))
        log.info(
            "Queuing the following list of principals for addition to KDC: {principals}".format(
                principals=newPrincipals
            )
        )

        # Just stack the principal names on the array until the user calls `finalize`
        self.principals += newPrincipals

    def save_keytab_secret(self) -> None:
        """
        Commits the previously added principals using `add_principal`, extracts a keytab fo the
        specific principals and then uploads it as a secret on DC/OS.
        :param secret_path: The path of the secret where to upload the keytab to
        """

        log.info(
            "Adding the following list of principals to KDC and creating secret {secret}: {principals}".format(
                principals=self.principals, secret=self.keytab_secret_path
            )
        )

        res = self.__kdc_api(
            "post",
            "principals",
            {
                "principals": self.principals,
                "secret": self.keytab_secret_path,
                "binary": self.keytab_secret_binary,
            },
        )
        if res.status_code != 200:
            raise RuntimeError("Unable to add principals")

        parsed = res.json()
        if parsed["status"] != "ok":
            raise RuntimeError(
                "Unable to add principals: {}".format(parsed.get("error", "Unknown error"))
            )

    def finalize(self) -> None:
        """
        Once the principals have been added, the rest of the environment setup does not ask for more info and can be
        automated, hence this method.
        """
        self.save_keytab_secret()

    def get_working_file_path(self, *args: str) -> str:
        if not self._working_dir:
            if not self._temp_working_dir:
                self._temp_working_dir = _create_temp_working_dir()
            self._working_dir: str = self._temp_working_dir.name

        working_filepath = os.path.join(self._working_dir, *args)
        return working_filepath

    def get_service_path(self) -> str:
        # Find the service name
        service_name_label = list(
            filter(lambda kv: kv[0] == "DCOS_SERVICE_NAME", self.app_definition["labels"].items())
        )
        assert len(service_name_label) == 1

        # Calculate the endpoint
        return "service/{}".format(service_name_label[0][1])

    def get_host(self) -> str:
        return sdk_hosts.autoip_host(service_name="marathon", task_name=self.app_definition["id"])

    def get_port(self) -> str:
        return str(self.app_definition["portDefinitions"][0]["port"])

    def get_api_port(self) -> str:
        return str(self.app_definition["portDefinitions"][1]["port"])

    def get_keytab_path(self) -> str:
        if self.keytab_secret_binary:
            return self.keytab_secret_path
        else:
            return "__dcos_base64__{}".format(self.keytab_secret_path)

    def set_keytab_path(self, secret_path: str, is_binary: bool) -> None:
        self.keytab_secret_path = secret_path
        self.keytab_secret_binary = is_binary

    def get_realm(self) -> str:
        return self.kdc_realm

    def get_kdc_address(self) -> str:
        return ":".join(str(p) for p in [self.get_host(), self.get_port()])

    def get_kdc_api_address(self) -> str:
        """
        The KDC API is exposed as an AdminRouter service on DC/OS
        """
        return ":".join(str(p) for p in [self.get_host(), self.get_api_port()])

    def get_principal(self, primary: str, instance: Optional[str] = None) -> str:

        if instance:
            principal = "{}/{}".format(primary, instance)
        else:
            principal = primary

        return "{}@{}".format(principal, self.get_realm())

    def cleanup(self) -> None:
        sdk_security.install_enterprise_cli()

        log.info("Removing the marathon KDC app")
        sdk_marathon.destroy_app(self.app_definition["id"])

        if self._temp_working_dir and isinstance(
            self._temp_working_dir, tempfile.TemporaryDirectory
        ):
            log.info("Deleting temporary working directory")
            self._temp_working_dir.cleanup()

        sdk_security.delete_service_account(
            service_account_name=KDC_SERVICE_ACCOUNT,
            service_account_secret=KDC_SERVICE_ACCOUNT_SECRET,
        )

        # TODO: separate secrets handling into another module
        log.info("Deleting keytab secret")
        sdk_security.install_enterprise_cli()
        sdk_security.delete_secret(self.get_keytab_path())
