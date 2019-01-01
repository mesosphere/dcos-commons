import os
import logging
import textwrap
import traceback

import sdk_hosts
import sdk_jobs
import sdk_plan
import sdk_utils
import string
import random



def get_foldered_service_name():
    return sdk_utils.get_foldered_name(SERVICE_NAME)

def get_random_string(char_count = 8):
    return ''.join([random.choice(string.ascii_letters + string.digits) for n in range(char_count)])

# Portworx service specific configurations
PACKAGE_NAME = 'portworx'
SERVICE_NAME = 'portworx'
PX_CLEANUP_SCRIPT_PATH = 'frameworks/portworx/scripts/px_dcos_cleanup.sh'
PX_TIMEOUT = 5 * 60 # 5 minutes timeout for portworx operations.
DEFAULT_TASK_COUNT = 1

PX_KVDB_SERVER = os.environ['KVDB']

PX_CLUSTER_NAME = "portworx-dcos-" + get_random_string() 

PX_NODE_OPTIONS = { "node": { "portworx_options": "-a -x mesos -d enp0s8 -m enp0s8",
                            "kvdb_servers": PX_KVDB_SERVER,
                            "count": DEFAULT_TASK_COUNT,
                            "portworx_cluster": PX_CLUSTER_NAME,
                  } }


