# This file contains portworx specific utilities needed in test automation.
# These utility functions plays important role in portworx functionality verification.

import sdk_install
import sdk_cmd
import logging
import datetime
from datetime import timedelta
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
    start_time = datetime.datetime.now()
    while start_time + timedelta(seconds = config.PX_TIMEOUT) > datetime.datetime.now():
        plan_status = check_plan_status(plan_name)
        if "COMPLETE" != plan_status:
            log.info("Portworx Plan status is: {}".format(plan_status))
            sleep(5)
            continue
        else:
            break
    return plan_status

def get_px_node_count():
    cmd = "portworx status | jq '. | .Nodes | length'"
    _, px_node_count, std_err = sdk_cmd.run_raw_cli(cmd)
    
    if not is_int(px_node_count):
            return -9999
    return int(px_node_count)

def check_px_status(do_not_wait = 0):
    cmd = "portworx status | jq -r '.Status'"
    count = 0
    
    while count < config.PX_TIMEOUT:
        _, px_status, std_err = sdk_cmd.run_raw_cli(cmd)
        if not is_int(px_status):
            count = count + 10 # Don't wait for too much time in this case 
            sleep(10)
            px_status = -999
            if do_not_wait:
                break
            continue

        if 2 != int(px_status):
            log.info("Portworx status is: {}".format(px_status))
            count = count + 5
            sleep(5)
            if do_not_wait:
                break
            continue
        else:
            break

    return int(px_status)

def px_get_portworx_version(pod_name):
    agent_id = px_get_agent_id(pod_name)
    cmd = "node ssh  \"pxctl --version\" --user=" + config.PX_AGENT_USER + " --mesos-id=" + agent_id + " --option StrictHostKeyChecking=no"
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    log.info("PortworxCMD:{} cli output: {}, cli error: {}".format(cmd, std_out, std_err))
    return std_out

def px_service_suspend_resume(pod_name):
    agent_id = px_get_agent_id(pod_name)
    cmd = "node ssh  \"sudo systemctl stop portworx\" --user=" + config.PX_AGENT_USER + " --mesos-id=" + agent_id + " --option StrictHostKeyChecking=no"
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    log.info("PortworxCMD:{} cli output: {}, cli error: {}".format(cmd, std_out, std_err))
    sleep(20) # Lets wait for 20 seconds?
    px_status = check_px_status(1)
    if px_status == 2:
        log.info("PORTWORX: Service has not stoped")
        raise

    cmd = "node ssh  \"sudo systemctl start portworx\" --user=" + config.PX_AGENT_USER + " --mesos-id=" + agent_id + " --option StrictHostKeyChecking=no"
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)

def px_get_vol_size_in_gb(vol_name):
    gb_factor = 1024 * 1024 * 1024
    cmd = "portworx volume list | jq '.[] | select(.locator.name == \"" + vol_name +"\") | .spec.size'" 

    count = 5
    while count > 0:
        _, vol_size, std_err = sdk_cmd.run_raw_cli(cmd)
        if not is_int(vol_size):
            sleep(5)
            count = count - 1
            continue
        break

    if not is_int(vol_size):
        return -9999

    vol_size = int(vol_size)/gb_factor
    return vol_size

def px_is_vol_encrypted(vol_name):
    cmd = "portworx volume list | jq '.[] | select(.locator.name == \"" + vol_name +"\") | .spec.encrypted'"
    _, vol_type, std_err = sdk_cmd.run_raw_cli(cmd)
    if vol_type == "true":
        return True
    else:
        return False

def px_create_volume(pod_name, vol_name = "dcos_test_vol", size = 10):
    agent_id = px_get_agent_id(pod_name)
    
    cmd = "node ssh  \"pxctl volume create " + vol_name + " --size=" + str(size) + "\" --user=" + config.PX_AGENT_USER + " --mesos-id=" + agent_id + " --option StrictHostKeyChecking=no"
    ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    if ret_val:
        log.info("PORTWORX: Failed volume creation, node id: {}, volume name: {}, size:{}, with error:{}".format(agent_id, vol_name, size, std_err))
        raise

def px_delete_volume(pod_name, vol_name):
    agent_id = px_get_agent_id(pod_name)
    
    cmd = "node ssh  \"pxctl volume delete " + vol_name + " -f \" --user=" + config.PX_AGENT_USER + " --mesos-id=" + agent_id + " --option StrictHostKeyChecking=no"
    ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    if ret_val:
        log.info("PORTWORX: Failed volume delete, node id: {}, volume name: {}, with error:{}".format(agent_id, vol_name, std_err))
        raise


def px_update_volume_size(pod_name, vol_name, new_size):
    agent_id = px_get_agent_id(pod_name)
    
    cmd = "node ssh  \"pxctl volume update --size=" + str(new_size) + " " + vol_name + "\" --user=" + config.PX_AGENT_USER + " --mesos-id=" + agent_id + " --option StrictHostKeyChecking=no"
    ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    if ret_val:
        log.info("PORTWORX: Failed volume update operation, node id: {}, volume name: {}, size:{}, with error:{}".format(agent_id, vol_name, size, std_err))
        raise

# command: /opt/pwx/bin/pxctl secrets dcos login --username px_user1 --password px_user1_password --base-path pwx/secrets
def px_dcos_login(pod_name, user_name, user_password, base_path):
    agent_id = px_get_agent_id(pod_name)

    cmd = "node ssh  \"pxctl secrets dcos login --username " + user_name + " --password " + user_password + " --base-path " + base_path + "\" --user=" + config.PX_AGENT_USER + " --mesos-id=" +  agent_id + " --option StrictHostKeyChecking=no"
    ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    if ret_val:
        log.info("PORTWORX: Failed dcos secrets login on node id: {}, user name: {}, user password:{}, base path: {}, with error:{}".format(agent_id, user_name, user_password, base_path, std_err))
        raise

# command: sudo systemctl restart portworx
def px_restart_portworx_service(pod_name):
    agent_id = px_get_agent_id(pod_name)
    cmd = "node ssh  \"sudo systemctl restart portworx\" --user=" + config.PX_AGENT_USER + " --mesos-id=" + agent_id + " --option StrictHostKeyChecking=no"
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    log.info("PortworxCMD:{} cli output: {}, cli error: {}".format(cmd, std_out, std_err))
    sleep(20) # Lets wait for 20 seconds?
    px_status = check_px_status()
    if px_status != 2:
        log.info("PORTWORX: Service has not restarted.")
        raise

# command: /opt/pwx/bin/pxctl volume create --secure --secret_key px_skey  enc_vol1
def px_create_encrypted_volume(pod_name, vol_name, secret_key):
    agent_id = px_get_agent_id(pod_name)

    cmd = "node ssh  \"pxctl volume create --secure --secret_key " + secret_key + " " + vol_name + "\" --user=" + config.PX_AGENT_USER + " --mesos-id=" + agent_id + " --option StrictHostKeyChecking=no"
    ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    if ret_val:
        log.info("PORTWORX: Failed encrypted  volume creation, node id: {}, volume name: {}, with error:{}".format(agent_id, vol_name, std_err))
        raise
