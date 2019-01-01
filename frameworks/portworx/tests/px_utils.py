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

def get_px_pod_list():
    cmd = "portworx pod list"
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    return std_out

def restart_pod(pod_name):
    cmd = "portworx pod restart " + pod_name
    _, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    return std_out

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
            return -9999

        if 2 != int(px_status):
            log.info("Portworx status is: {}".format(px_status))
            count = count + 5
            sleep(5)
            continue
        else:
            break
    return int(px_status)
