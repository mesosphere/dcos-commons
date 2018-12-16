"""Utilities relating to mapping tasks and services to hostnames

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_hosts IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import json
import retrying

import sdk_cmd
import sdk_utils


AUTOIP_HOST_SUFFIX = "autoip.dcos.thisdcos.directory"
VIP_HOST_SUFFIX = "l4lb.thisdcos.directory"
MARATHON_HOST_PREFIX = "marathon"


def autoip_host(service_name, task_name, port=-1):
    """Returns the autoip hostname for the container of a given task, with handling of foldered services.
    In CNI cases, this may vary from the host of the agent system."""
    return _to_host(_safe_name(task_name), _safe_name(service_name), AUTOIP_HOST_SUFFIX, port)


def custom_host(service_name, task_name, custom_domain, port=-1):
    """
    Returns a properly constructed hostname for the container of the given task using the
    supplied custom domain.
    """
    return _to_host(_safe_name(task_name), _safe_name(service_name), custom_domain, port)


def vip_host(service_name, vip_name, port=-1):
    """Returns the hostname of a specified service VIP, with handling of foldered services."""
    return _to_host(_safe_name(vip_name), _safe_name(service_name), VIP_HOST_SUFFIX, port)


def scheduler_vip_host(service_name, vip_name, port=-1):
    """Returns the scheduler hostname of a specified service VIP, with handling of foldered services.

    e.g.: scheduler_vip_host("cassandra", "api") == "api.cassandra.marathon.l4lb.thisdcos.directory"
    """
    return _to_host(
        _safe_name(vip_name),
        _safe_name(service_name),
        "{}.{}".format(MARATHON_HOST_PREFIX, VIP_HOST_SUFFIX),
        port,
    )


def _safe_name(name):
    """Converts a potentially slash-delimited name to one that works for 'thisdcos.directory'
    hostnames used by autoip and vips. In both cases the slashes may just be stripped out."""
    return name.replace("/", "")


def _safe_mesos_dns_taskname(task_name):
    """Converts a potentially slash-delimited task name to one that works for '.mesos' task names
    Mesos DNS task names handle folders like this: /path/to/myservice => myservice-to-path"""
    elems = task_name.strip("/").split("/")
    elems.reverse()
    return "-".join(elems)


def _to_host(host_first, host_second, host_third, port):
    host = "{}.{}.{}".format(host_first, host_second, host_third)
    if port != -1:
        return "{}:{}".format(host, port)
    return host


def get_foldered_dns_name(service_name):
    if sdk_utils.dcos_version_less_than("1.10"):
        return service_name
    return sdk_utils.get_foldered_name(service_name).replace("/", "")


@retrying.retry(wait_fixed=2000, stop_max_delay=5 * 60 * 1000)
def get_crypto_id_domain():
    """
    Returns the cluster cryptographic ID equivalent of autoip.dcos.thisdcos.directory.

    These addresses are routable within the cluster but can be used to test setting a custom
    service domain.
    """
    rc, stdout, _ = sdk_cmd.master_ssh("curl localhost:62080/lashup/key/")
    assert rc == 0

    crypto_id = json.loads(stdout.strip())["zbase32_public_key"]

    return "autoip.dcos.{}.dcos.directory".format(crypto_id)
