from tests import config
import pytest
import sdk_cmd
import sdk_install
import sdk_tasks
import logging
log = logging.getLogger(__name__)


@pytest.mark.sanity
def test_xmx_and_xms_flags():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    #setting custom values for the heap of various pods
    master_nodes_heap = 1024
    data_nodes_heap = 2044
    coordinator_nodes_heap = 2048
    ingest_nodes_heap = 1408
    #installing elastic service and passing customized json to overwrite default values.
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_TASK_COUNT,
        {
        "master_nodes": {
            "heap": {
                "size": 200
        } },
        "data_nodes": {
            "heap": {
                "size": 300
        } },
        "coordinator_nodes": {
            "heap": {
                "size": 208
        } },
        "ingest_nodes": {
            "heap": {
                "size": 204
        } }
    }
    )
   #getting all the tasks and checking the flag duplicacy by running curl_cmd command.
    for task in sdk_tasks.get_task_ids(config.SERVICE_NAME):
        cmd = "ps aux"
        log.info(cmd)
        flag_xms = "Xms"
        flag_xmx = "Xmx"
        exit_code, stdout, stderr = sdk_cmd.service_task_exec(config.SERVICE_NAME,task,cmd)
        if(str(task).count("master")):
            master_xms = flag_xms + str(master_nodes_heap)
            master_xmx = flag_xmx + str(master_nodes_heap)
            log.info("Checking flags in master node: " + task)
            assert str(stdout).count(master_xms) == 1, "Default master xms flag prefix should appear once"
            assert str(stdout).count(master_xmx) == 1, "Default master xmx flag prefix should appear once"
        elif(str(task).count("data")):
            data_xms = flag_xms + str(data_nodes_heap)
            data_xmx = flag_xmx + str(data_nodes_heap)
            log.info("Checking flags in data node: " + task)
            assert str(stdout).count(data_xms) == 1, "Default data xms flag prefix should appear once"
            assert str(stdout).count(data_xmx) == 1, "Default data xmx flag prefix should appear once"
        elif(str(task).count("coordinator")):
            coordinator_xms = flag_xms + str(coordinator_nodes_heap)
            coordinator_xmx = flag_xmx + str(coordinator_nodes_heap)
            log.info("Checking flags in coordinator node: " + task)
            assert str(stdout).count(coordinator_xms) == 1, "Default coordinator xms flag prefix should appear once"
            assert str(stdout).count(coordinator_xmx) == 1, "Default coordinator xmx flag prefix should appear once"
        elif(str(task).count("ingest")):
            ingest_xms = flag_xms + str(ingest_nodes_heap)
            ingest_xmx = flag_xmx + str(ingest_nodes_heap)
            log.info("Checking flags in ingest node: " + task)
            assert str(stdout).count(ingest_xms) == 1, "Default ingest flag xms prefix should appear once"
            assert str(stdout).count(ingest_xmx) == 1, "Default ingest flag xmx prefix should appear once"
        else:
            log.info("----------------Unknown option-----------------------------------")





    #uninstalling the installed service
    #sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
