# This file contains dcos specific utilities needed for portworx framework. 

import sdk_install
import sdk_cmd
import logging
from tests import config
from time import sleep


log = logging.getLogger(__name__)

def install_enterprise_cli():
    cmd = "package install dcos-enterprise-cli --yes"

    count = 5
    while count > 0:
        ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
        if ret_val:
            count = count - 1
            sleep(5)
            continue
        break

    if ret_val:
        log.info("DCOS: Failed install dcos-enterprises-cli, with error:{}".format(std_err))
        raise

# command: dcos --version
def get_dcos_version():
    cmd = " --version"
    ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    return std_out

# command: dcos security org groups create pxservice
def create_dcos_security_group(group_name):
    cmd = "security org groups create " + group_name
    ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    
    if ret_val:
        log.info("DCOS: Failed to create dcos groupi {}, with error:{}".format(group_name, std_err))
        raise

# command: dcos security org users create --description "Portworx test user" --password "px_user1" px_user1
def create_dcos_user(user_name, user_password):
    cmd = "security org users create --description \"Portworx test user\" --password " + user_password + " " + user_name
    ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    
    if ret_val:
        log.info("DCOS: Failed to create dcos user {}, with error:{}".format(user_name, std_err))
        raise

# command: dcos security org groups add_user pxservices px_user1
def add_user_to_group(user_name, group_name):
    cmd = "security org groups add_user " + group_name + " " + user_name
    ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    
    if ret_val:
        log.info("DCOS: Failed to add user {} in group {}, with error:{}".format(user_name, group_name, std_err))
        raise

# command: dcos security org users grant px_user1 dcos:secrets:default:pwx/secrets/* full
def grant_permissions_to_user(user_name, base_path):
    cmd = "security org users grant " + user_name + " dcos:secrets:default:/" + base_path + "/* full"
    ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    
    if ret_val:
        log.info("DCOS: Failed to grant permissions to user {}, with error:{}".format(user_name, std_err))
        raise

# command: dcos security secrets create --value=px_secrets  pwx/secrets/cluster-wide-secret-key
def create_dcos_secrets(secrets_value, base_path, secret_key):
    cmd = "security secrets create --value=" + secrets_value + " " + base_path + "/" + secret_key
    ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    
    if ret_val:
        log.info("DCOS: Failed to create secrets {}, with error:{}".format(secrets_value, std_err))
        raise

# command: dcos security secrets list pwx/secrets 
def check_secret_present(secrets_path, secrets_key):
    cmd = "security secrets list " + secrets_path
    ret_val, std_out, std_err = sdk_cmd.run_raw_cli(cmd)
    
    if ret_val:
        log.info("DCOS: Failed to list secrets for path {}, with error:{}".format(secrets_path, std_err))
        raise
    
    secrets_list = std_out.split(' ')
    log.info("DCOS: Secrets list:{}, secret key; {}".format(secrets_list, secrets_key))
    if secrets_key not in secrets_list:
        return False
    
    return True
