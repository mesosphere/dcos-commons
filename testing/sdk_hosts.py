'''Utilities relating to mapping tasks and services to hostnames

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_hosts IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import logging

import sdk_tasks
import sdk_utils

LOG = logging.getLogger(__name__)


SYSTEM_HOST_SUFFIX = 'mesos'
AUTOIP_HOST_SUFFIX = 'autoip.dcos.thisdcos.directory'
VIP_HOST_SUFFIX = 'l4lb.thisdcos.directory'


def system_host(service_name, task_name, port=-1):
    '''Returns the mesos DNS name for the host machine of a given task, with handling of foldered services.
    This maps to the host IP, which may be different from the container IP if CNI is enabled.

    service=marathon task=/path/to/scheduler =>  scheduler-to-path.marathon.mesos
    service=/path/to/scheduler task=node-0   =>  node-0.pathtoscheduler.mesos

    See also: https://dcos.io/docs/1.8/usage/service-discovery/dns-overview/'''
    return _to_host(
        _safe_mesos_dns_taskname(task_name),
        _safe_name(service_name),
        SYSTEM_HOST_SUFFIX,
        port)


def autoip_host(service_name, task_name, port=-1):
    '''Returns the autoip hostname for the container of a given task, with handling of foldered services.
    In CNI cases, this may vary from the host of the agent system.'''
    return _to_host(
        _safe_name(task_name),
        _safe_name(service_name),
        AUTOIP_HOST_SUFFIX,
        port)


def vip_host(service_name, vip_name, port=-1):
    '''Returns the hostname of a specified service VIP, with handling of foldered services.'''
    return _to_host(
        _safe_name(vip_name),
        _safe_name(service_name),
        VIP_HOST_SUFFIX,
        port)


def _safe_name(name):
    '''Converts a potentially slash-delimited name to one that works for 'thisdcos.directory'
    hostnames used by autoip and vips. In both cases the slashes may just be stripped out.'''
    return name.replace('/', '')


def _safe_mesos_dns_taskname(task_name):
    '''Converts a potentially slash-delimited task name to one that works for '.mesos' task names
    Mesos DNS task names handle folders like this: /path/to/myservice => myservice-to-path'''
    elems = task_name.strip('/').split('/')
    elems.reverse()
    return '-'.join(elems)


def _to_host(host_first, host_second, host_third, port):
    host = '{}.{}.{}'.format(host_first, host_second, host_third)
    if port != -1:
        return '{}:{}'.format(host, port)
    return host


def resolve_hosts(task_id: str, hosts: list) -> bool:
    """
    Use bootstrap to resolve the specified list of hosts
    """
    bootstrap_cmd = ['./bootstrap',
                     '-print-env=false',
                     '-template=false',
                     '-install-certs=false',
                     '-resolve-hosts', ','.join(hosts)]
    LOG.info("Running bootstrap to wait for DNS resolution of %s\n\t%s", hosts, bootstrap_cmd)
    return_code, bootstrap_stdout, bootstrap_stderr = sdk_tasks.task_exec(task_id, ' '.join(bootstrap_cmd))

    LOG.info("bootstrap return code: %s", return_code)
    LOG.info("bootstrap STDOUT: %s", bootstrap_stdout)
    LOG.info("bootstrap STDERR: %s", bootstrap_stderr)

    # Note that bootstrap returns its output in STDERR
    resolved = 'SDK Bootstrap successful.' in bootstrap_stderr
    if not resolved:
        for host in hosts:
            resolved_host_string = "Resolved '{host}' =>".format(host=host)
            host_resolved = resolved_host_string in bootstrap_stdout
            if not host_resolved:
                LOG.error("Could not resolve: %s", host)

    return resolved


def get_foldered_dns_name(service_name):
    if sdk_utils.dcos_version_less_than('1.10'):
        return service_name
    return sdk_utils.get_foldered_name(service_name).replace("/", "")
