# This file contains portworx specific utilities needed in test automation.
# These utility functions plays important role in portworx functionality verification.

import sdk_install
import sdk_cmd
import logging
from tests import config
from time import sleep

log = logging.getLogger(__name__)

def is_int(string):
    try:
        int(string)
        return True
    except ValueError:
        return False

def get_px_options_kvdb_image():
    cmd = "marathon app show /portworx | jq '.env.PORTWORX_OPTIONS'"
    _, px_options, std_err = sdk_cmd.run_raw_cli(cmd)
    cmd = "marathon app show /portworx | jq '.env.PORTWORX_KVDB_SERVERS'"
    _, px_kvdb, std_err = sdk_cmd.run_raw_cli(cmd)
    cmd = "marathon app show /portworx | jq '.env.PORTWORX_IMAGE_NAME'"
    _, px_image, std_err = sdk_cmd.run_raw_cli(cmd)

    return px_options.strip(), px_kvdb.strip(), px_image.strip()

def get_px_pod_list():
    cmd = "portworx pod list"
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    log.info("Portworx pod list is: {}".format(std_out))
    
    pod_list = std_out.split('\n') 
    pod_count = len(pod_list) - 2   # Reduce count by 2 for '[' and ']'
    if pod_count <= 0:
        pod_list = []

    return pod_count, pod_list

def restart_pod(pod_name):
    cmd = "portworx pod restart " + pod_name
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)

    return std_out

def px_get_agent_id(pod_name):
    cmd = "portworx pod info " + pod_name + " | jq '.[].info.slaveId.value'"
    pod_agent_id = sdk_cmd.run_raw_cli(cmd)
    return pod_agent_id[1]

def replace_pod(pod_name):
    pod_agent_id_old = px_get_agent_id(pod_name)
    
    cmd = "portworx pod replace " + pod_name
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    sleep(10)  # Is this sufficient time?
    
    pod_agent_id_new = px_get_agent_id(pod_name)
    log.info("PORTWORX: Old pod agent id: {} , New pod agent id: {}".format(pod_agent_id_old, pod_agent_id_new))

    return pod_agent_id_old, pod_agent_id_new
    
def start_plan(plan_name = "deploy"):
    cmd = "portworx plan start " + plan_name
    sdk_cmd.run_raw_cli(cmd)

def stop_plan(plan_name = "deploy"):
    cmd = "portworx plan stop " + plan_name
    sdk_cmd.run_raw_cli(cmd)

def force_restart_plan(plan_name = "deploy"):
    cmd = "portworx plan force-restart " + plan_name
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)

    return std_out

def check_plan_status(plan_name):
    cmd = "portworx plan status " + plan_name + " --json | jq -r '.status' "
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)

    return std_out

def wait_for_plan_complete(plan_name):
    count = 0
    
    while count < config.PX_TIMEOUT:
        plan_status = check_plan_status(plan_name)
        if "COMPLETE" != plan_status:
            log.info("Portworx Plan status is: {}".format(plan_status))
            count = count + 5
            sleep(5)
            continue
        else:
            break
    return plan_status

def get_px_node_count():
    cmd = "portworx status | jq '. | .Nodes | length'"
    _, px_node_count, std_err = sdk_cmd.run_raw_cli(cmd)
    
    if True != is_int(px_node_count):
            return -9999
    return int(px_node_count)

def check_px_status():
    cmd = "portworx status | jq -r '.Status'"
    count = 0
    
    while count < config.PX_TIMEOUT:
        _, px_status, std_err = sdk_cmd.run_raw_cli(cmd)
        if True != is_int(px_status):
            count = count + 50 # Don't wait too much time in this case 
            sleep(10)
            px_status = -999
            continue

        if 2 != int(px_status):
            log.info("Portworx status is: {}".format(px_status))
            count = count + 5
            sleep(5)
            continue
        else:
            break

    return int(px_status)

def px_service_suspend_resume(pod_name):
    agent_id = px_get_agent_id(pod_name)
    agent_id = str(agent_id)
    cmd = "node ssh  \"sudo systemctl stop portworx\" --user=" + config.PX_AGENT_USER + " --mesos-id=" + agent_id
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    log.info("PortworxCMD:{} XXX cli output: {}, cli error: {}".format(cmd, std_out, std_err))
    px_status = check_px_status()
    if px_status == 2:
        log.info("PORTWORX: Service has not stoped")
        raise

    cmd = "node ssh  \"sudo systemctl start portworx\" --user=" + config.PX_AGENT_USER + " --mesos-id=" + agent_id
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
