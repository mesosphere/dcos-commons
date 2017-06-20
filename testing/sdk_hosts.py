'''Utilities relating to mapping tasks and services to hostnames'''


SYSTEM_HOST_SUFFIX = 'mesos'
AUTOIP_HOST_SUFFIX = 'autoip.dcos.thisdcos.directory'
VIP_HOST_SUFFIX = 'l4lb.thisdcos.directory'


def system_host(service_name, task_name, port=-1):
    '''Returns the mesos DNS name for the host machine of a given task, with handling of foldered services.
    This maps to the host IP, which may be different from the container IP if CNI is enabled.'''
    return _to_host(
        _safe_mesos_dns_name(task_name),
        _safe_mesos_dns_name(service_name),
        SYSTEM_HOST_SUFFIX,
        port)


def autoip_host(service_name, task_name, port=-1):
    '''Returns the autoip hostname for the container of a given task, with handling of foldered services.
    In CNI cases, this may vary from the host of the agent system.'''
    return _to_host(
        _safe_directory_name(task_name),
        _safe_directory_name(service_name),
        AUTOIP_HOST_SUFFIX,
        port)


def vip_host(service_name, vip_name, port=-1):
    '''Returns the hostname of a specified service VIP, with handling of foldered services.'''
    return _to_host(
        _safe_directory_name(vip_name),
        _safe_directory_name(service_name),
        VIP_HOST_SUFFIX,
        port)


def _safe_directory_name(name):
    '''Converts a potentially slash-delimited name to one that works for 'thisdcos.directory'
    hostnames used by autoip and vips. In both cases the slashes may just be stripped out.'''
    return name.replace('/', '')


def _safe_mesos_dns_name(name):
    '''Converts a potentially slash-delimited name to one that works for '.mesos' hostnames
    Mesos DNS names handle folders like this: /path/to/myservice:mytask => mytask.service.to.path.mesos'''
    elems = name.split('/')
    elems.reverse()
    return '.'.join(elems)


def _to_host(host_first, host_second, host_third, port):
    host = '{}.{}.{}'.format(host_first, host_second, host_third)
    if port != -1:
        return '{}:{}'.format(host, port)
    return host
