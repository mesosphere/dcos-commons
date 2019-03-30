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
    master_node_heap = 102
    data_node_heap = 204
    coordinator_node_heap = 208
    ingest_node_heap = 288
    #installing elastic service and passing customized json to overwrite default values.
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_TASK_COUNT,
        {
        "master_nodes": {
            "heap": {
                "size": master_node_heap
        } },
        "data_nodes": {
            "heap": {
                "size": data_node_heap
        } },
        "coordinator_nodes": {
            "heap": {
                "size": coordinator_node_heap
        } },
        "ingest_nodes": {
            "heap": {
                "size": ingest_node_heap
        } }
    }
    )
   #getting all the tasks and checking the flag duplicacy by running curl_cmd command.
    for task in sdk_tasks.get_task_ids(config.SERVICE_NAME):
        cmd = "ps aux"
        flag_xms = "Xms"
        flag_xmx = "Xmx"
        exit_code, stdout, stderr = sdk_cmd.service_task_exec(config.SERVICE_NAME,task,cmd)

        if(str(task).count("master") and str(stderr).count("elastic")==1):
            master_xms = flag_xms + str(master_node_heap)
            master_xmx = flag_xmx + str(master_node_heap)
            log.info("Checking flags in master node: " + task)
            assert str(stdout).count(master_xms) == 1, "Default master node xms flag prefix should appear once"
            assert str(stdout).count(master_xmx) == 1, "Default master node xmx flag prefix should appear once"
        if(str(task).count("data") and str(stderr).count("elastic")==1):
            data_xms = flag_xms + str(data_node_heap)
            data_xmx = flag_xmx + str(data_node_heap)
            log.info("Checking flags in data node: " + task)
            assert str(stdout).count(data_xms) == 1, "Default data node xms flag prefix should appear once"
            assert str(stdout).count(data_xmx) == 1, "Default data node xmx flag prefix should appear once"
        if(str(task).count("coordinator") and str(stderr).count("elastic")==1):
            coordinator_xms = flag_xms + str(coordinator_node_heap)
            coordinator_xmx = flag_xmx + str(coordinator_node_heap)
            log.info("Checking flags in coordinator node: " + task)
            assert str(stdout).count(coordinator_xms) == 1, "Default coordinator node xms flag prefix should appear once"
            assert str(stdout).count(coordinator_xmx) == 1, "Default coordinator node xmx flag prefix should appear once"
        if(str(task).count("ingest") and str(stderr).count("elastic")==1):
            ingest_xms = flag_xms + str(ingest_node_heap)
            ingest_xmx = flag_xmx + str(ingest_node_heap)
            log.info("Checking flags in ingest node: " + task)
            assert str(stdout).count(ingest_xms) == 1, "Default ingest node flag xms prefix should appear once"
            assert str(stdout).count(ingest_xmx) == 1, "Default ingest node flag xmx prefix should appear once"






    #uninstalling the installed service
    #sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
