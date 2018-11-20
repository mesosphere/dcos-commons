import os
import logging
import textwrap
import traceback

import sdk_hosts
import sdk_jobs
import sdk_plan
import sdk_utils

# Portworx service specific configurations
PACKAGE_NAME = 'portworx'
SERVICE_NAME = 'portworx'
PX_CLEANUP_SCRIPT_PATH = 'frameworks/portworx/scripts/px_dcos_cleanup.sh'

DEFAULT_TASK_COUNT = 3
DEFAULT_CASSANDRA_TIMEOUT = 600

DEFAULT_NODE_ADDRESS = os.getenv('PORTWORX_NODE_ADDRESS', sdk_hosts.autoip_host(SERVICE_NAME, 'node-0-server'))

PX_NODE_OPTIONS = { "node": { "portworx_options": "-a -x mesos -d enp0s8 -m enp0s8",
                            "kvdb_servers": "etcd:http://70.0.5.211:2379,etcd:http://70.0.5.212:2379,etcd:http://70.0.5.213:2379",
                  } }

def get_foldered_service_name():
    return sdk_utils.get_foldered_name(SERVICE_NAME)
