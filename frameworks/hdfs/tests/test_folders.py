import json
import pytest
import xml.etree.ElementTree as etree

from tests.config import *
import sdk_cmd as cmd
import sdk_install as install
import sdk_plan as plan
import sdk_utils as utils

FOLDERED_SERVICE_NAME = "/path/to/" + PACKAGE_NAME


def uninstall_foldered():
    install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    uninstall_foldered()


def teardown_module(module):
    uninstall_foldered()


@pytest.mark.sanity
@pytest.mark.smoke
def test_install_foldered():
    install.install(
        PACKAGE_NAME,
        DEFAULT_TASK_COUNT,
        service_name=FOLDERED_SERVICE_NAME,
        additional_options={"service": { "name": FOLDERED_SERVICE_NAME } })
    plan.wait_for_completed_deployment(FOLDERED_SERVICE_NAME)

    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    core_site = etree.fromstring(cmd.run_cli('hdfs --name={} endpoints core-site.xml'.format(FOLDERED_SERVICE_NAME)))
    check_properties(core_site, {
        'ha.zookeeper.parent-znode': '/dcos-service-path__to__hdfs/hadoop-ha'
    })

    framework_host = 'pathtohdfs.autoip.dcos.thisdcos.directory'
    hdfs_site = etree.fromstring(cmd.run_cli('hdfs --name={} endpoints hdfs-site.xml'.format(FOLDERED_SERVICE_NAME)))
    expect = {
        'dfs.namenode.shared.edits.dir': 'qjournal://' + ';'.join(['journal-{}-node.{}:8485'.format(i, framework_host) for i in range(3)]) + '/hdfs',
    }
    for i in range(2):
        expect['dfs.namenode.rpc-address.hdfs.name-{}-node'.format(i)] = 'name-{}-node.{}:9001'.format(i, framework_host)
        expect['dfs.namenode.http-address.hdfs.name-{}-node'.format(i)] = 'name-{}-node.{}:9002'.format(i, framework_host)
    check_properties(hdfs_site, expect)


def check_properties(xml, expect):
    found = {}
    for prop in xml.findall('property'):
        name = prop.find('name').text
        if name in expect:
            found[name] = prop.find('value').text
    utils.out('expect: {}\nfound:  {}'.format(expect, found))
    assert expect == found
