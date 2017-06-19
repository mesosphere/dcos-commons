'''Utilities relating to mapping tasks and services to hostnames'''


SYSTEM_HOST_SUFFIX = 'mesos'
AUTOIP_HOST_SUFFIX = 'autoip.dcos.thisdcos.directory'
VIP_HOST_SUFFIX = 'l4lb.thisdcos.directory'


def system_host(service_name, task_name, port=-1):
    # Mesos DNS names are like this: /path/to/myservice:mytask => mytask.service-to-path.mesos
    def mesos_dns_name(name):
        elems = name.split('/')
        elems.reverse()
        return '-'.join(elems)
    host = '{}.{}.{}'.format(mesos_dns_name(task_name), mesos_dns_name(service_name), SYSTEM_HOST_SUFFIX)
    return _with_port(host, port)


def autoip_host(service_name, task_name, port=-1):
    host = '{}.{}.{}'.format(task_name.replace('/', ''), service_name.replace('/', ''), AUTOIP_HOST_SUFFIX)
    return _with_port(host, port)


def vip_host(service_name, vip_name, port=-1):
    host = '{}.{}.{}'.format(task_name.replace('/', ''), service_name.replace('/', ''), VIP_HOST_SUFFIX)
    return _with_port(host, port)


def _with_port(host, port):
    if port != -1:
        return '{}:{}'.format(host, port)
    return host
