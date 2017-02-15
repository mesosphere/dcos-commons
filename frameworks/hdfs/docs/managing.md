---
post_title: Managing
menu_order: 50
feature_maturity: experimental
enterprise: 'no'
---

# Add a Data Node
Increase the `DATA_COUNT` value from the DC/OS dashboard as described in the Configuring section. This creates an update plan as described in that section. An additional node will be added as the last step of that plan.

## Node Info

Comprehensive information is available about every node.  To list all nodes:

```bash
dcos hdfs --name=<service-name> pods list
```

Result:
```json
[
  "data-0",
  "data-1",
  "data-2",
  "journal-0",
  "journal-1",
  "journal-2",
  "name-0",
  "name-1",
  "zkfc-0",
  "zkfc-1"
]
```

To view information about a node, run the following command from the CLI.
```bash
$ dcos hdfs --name=<service-name> pods info <node-id>
```

For example:
```bash
$ dcos hdfs pods info journal-0
```

Result:
```json
[
  {
    "info": {
      "name": "journal-0-node",
      "taskId": {
        "value": "journal-0-node__b31a70f4-73c5-4065-990c-76c0c704b8e4"
      },
      "slaveId": {
        "value": "0060634a-aa2b-4fcc-afa6-5569716b533a-S5"
      },
      "resources": [
        {
          "name": "cpus",
          "type": "SCALAR",
          "scalar": {
            "value": 0.3
          },
          "ranges": null,
          "set": null,
          "role": "hdfs-role",
          "reservation": {
            "principal": "hdfs-principal",
            "labels": {
              "labels": [
                {
                  "key": "resource_id",
                  "value": "4208f1ea-586f-4157-81fd-dfa0877e7472"
                }
              ]
            }
          },
          "disk": null,
          "revocable": null,
          "shared": null
        },
        {
          "name": "mem",
          "type": "SCALAR",
          "scalar": {
            "value": 512.0
          },
          "ranges": null,
          "set": null,
          "role": "hdfs-role",
          "reservation": {
            "principal": "hdfs-principal",
            "labels": {
              "labels": [
                {
                  "key": "resource_id",
                  "value": "a0be3c2c-3c7c-47ad-baa9-be81fb5d5f2e"
                }
              ]
            }
          },
          "disk": null,
          "revocable": null,
          "shared": null
        },
        {
          "name": "ports",
          "type": "RANGES",
          "scalar": null,
          "ranges": {
            "range": [
              {
                "begin": 8480,
                "end": 8480
              },
              {
                "begin": 8485,
                "end": 8485
              }
            ]
          },
          "set": null,
          "role": "hdfs-role",
          "reservation": {
            "principal": "hdfs-principal",
            "labels": {
              "labels": [
                {
                  "key": "resource_id",
                  "value": "d50b3deb-97c7-4960-89e5-ac4e508e4564"
                }
              ]
            }
          },
          "disk": null,
          "revocable": null,
          "shared": null
        },
        {
          "name": "disk",
          "type": "SCALAR",
          "scalar": {
            "value": 5000.0
          },
          "ranges": null,
          "set": null,
          "role": "hdfs-role",
          "reservation": {
            "principal": "hdfs-principal",
            "labels": {
              "labels": [
                {
                  "key": "resource_id",
                  "value": "3e624468-11fb-4fcf-9e67-ddb883b1718e"
                }
              ]
            }
          },
          "disk": {
            "persistence": {
              "id": "6bf7fcf1-ccdf-41a3-87ba-459162da1f03",
              "principal": "hdfs-principal"
            },
            "volume": {
              "mode": "RW",
              "containerPath": "journal-data",
              "hostPath": null,
              "image": null,
              "source": null
            },
            "source": null
          },
          "revocable": null,
          "shared": null
        }
      ],
      "executor": {
        "type": null,
        "executorId": {
          "value": "journal__e42893b5-9d96-4dfb-8e85-8360d483a122"
        },
        "frameworkId": null,
        "command": {
          "uris": [
            {
              "value": "https://downloads.mesosphere.com/hdfs/assets/1.0.0-2.6.0/executor.zip",
              "executable": null,
              "extract": null,
              "cache": null,
              "outputFile": null
            },
            {
              "value": "https://downloads.mesosphere.com/libmesos-bundle/libmesos-bundle-1.9-argus-1.1.x-2.tar.gz",
              "executable": null,
              "extract": null,
              "cache": null,
              "outputFile": null
            },
            {
              "value": "https://downloads.mesosphere.com/java/jre-8u112-linux-x64-jce-unlimited.tar.gz",
              "executable": null,
              "extract": null,
              "cache": null,
              "outputFile": null
            },
            {
              "value": "https://downloads.mesosphere.com/hdfs/assets/hadoop-2.6.0-cdh5.9.1-dcos.tar.gz",
              "executable": null,
              "extract": null,
              "cache": null,
              "outputFile": null
            },
            {
              "value": "https://downloads.mesosphere.com/hdfs/assets/1.0.0-2.6.0/bootstrap.zip",
              "executable": null,
              "extract": null,
              "cache": null,
              "outputFile": null
            },
            {
              "value": "http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/artifacts/template/25f791d8-4d42-458f-84fb-9d82842ffb3e/journal/node/core-site",
              "executable": null,
              "extract": false,
              "cache": null,
              "outputFile": "config-templates/core-site"
            },
            {
              "value": "http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/artifacts/template/25f791d8-4d42-458f-84fb-9d82842ffb3e/journal/node/hdfs-site",
              "executable": null,
              "extract": false,
              "cache": null,
              "outputFile": "config-templates/hdfs-site"
            },
            {
              "value": "http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/artifacts/template/25f791d8-4d42-458f-84fb-9d82842ffb3e/journal/node/hadoop-metrics2",
              "executable": null,
              "extract": false,
              "cache": null,
              "outputFile": "config-templates/hadoop-metrics2"
            }
          ],
          "environment": null,
          "shell": null,
          "value": "export LD_LIBRARY_PATH=$MESOS_SANDBOX/libmesos-bundle/lib:$LD_LIBRARY_PATH && export MESOS_NATIVE_JAVA_LIBRARY=$(ls $MESOS_SANDBOX/libmesos-bundle/lib/libmesos-*.so) && export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jre*/) && ./executor/bin/executor",
          "arguments": [],
          "user": null
        },
        "container": null,
        "resources": [],
        "name": "journal",
        "source": null,
        "data": null,
        "discovery": null,
        "shutdownGracePeriod": null,
        "labels": null
      },
      "command": {
        "uris": [],
        "environment": {
          "variables": [
            {
              "name": "PERMISSIONS_ENABLED",
              "value": "false"
            },
            {
              "name": "DATA_NODE_BALANCE_BANDWIDTH_PER_SEC",
              "value": "41943040"
            },
            {
              "name": "NAME_NODE_HANDLER_COUNT",
              "value": "20"
            },
            {
              "name": "CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE",
              "value": "1000"
            },
            {
              "name": "HADOOP_ROOT_LOGGER",
              "value": "INFO,console"
            },
            {
              "name": "HA_FENCING_METHODS",
              "value": "shell(/bin/true)"
            },
            {
              "name": "SERVICE_ZK_ROOT",
              "value": "dcos-service-hdfs"
            },
            {
              "name": "HADOOP_PROXYUSER_HUE_GROUPS",
              "value": "*"
            },
            {
              "name": "NAME_NODE_HEARTBEAT_RECHECK_INTERVAL",
              "value": "60000"
            },
            {
              "name": "HADOOP_PROXYUSER_HUE_HOSTS",
              "value": "*"
            },
            {
              "name": "CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS",
              "value": "1000"
            },
            {
              "name": "JOURNAL_NODE_RPC_PORT",
              "value": "8485"
            },
            {
              "name": "CLIENT_FAILOVER_PROXY_PROVIDER_HDFS",
              "value": "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider"
            },
            {
              "name": "DATA_NODE_HANDLER_COUNT",
              "value": "10"
            },
            {
              "name": "HA_AUTOMATIC_FAILURE",
              "value": "true"
            },
            {
              "name": "JOURNALNODE",
              "value": "true"
            },
            {
              "name": "NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION",
              "value": "4"
            },
            {
              "name": "HADOOP_PROXYUSER_HTTPFS_HOSTS",
              "value": "*"
            },
            {
              "name": "POD_INSTANCE_INDEX",
              "value": "0"
            },
            {
              "name": "DATA_NODE_IPC_PORT",
              "value": "9005"
            },
            {
              "name": "JOURNAL_NODE_HTTP_PORT",
              "value": "8480"
            },
            {
              "name": "NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK",
              "value": "false"
            },
            {
              "name": "TASK_USER",
              "value": "root"
            },
            {
              "name": "journal-0-node",
              "value": "true"
            },
            {
              "name": "HADOOP_PROXYUSER_ROOT_GROUPS",
              "value": "*"
            },
            {
              "name": "TASK_NAME",
              "value": "journal-0-node"
            },
            {
              "name": "HADOOP_PROXYUSER_ROOT_HOSTS",
              "value": "*"
            },
            {
              "name": "IMAGE_COMPRESS",
              "value": "true"
            },
            {
              "name": "CLIENT_READ_SHORTCIRCUIT",
              "value": "true"
            },
            {
              "name": "FRAMEWORK_NAME",
              "value": "hdfs"
            },
            {
              "name": "IMAGE_COMPRESSION_CODEC",
              "value": "org.apache.hadoop.io.compress.SnappyCodec"
            },
            {
              "name": "NAME_NODE_SAFEMODE_THRESHOLD_PCT",
              "value": "0.9"
            },
            {
              "name": "NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION",
              "value": "0.95"
            },
            {
              "name": "HADOOP_PROXYUSER_HTTPFS_GROUPS",
              "value": "*"
            },
            {
              "name": "CLIENT_READ_SHORTCIRCUIT_PATH",
              "value": "/var/lib/hadoop-hdfs/dn_socket"
            },
            {
              "name": "DATA_NODE_HTTP_PORT",
              "value": "9004"
            },
            {
              "name": "DATA_NODE_RPC_PORT",
              "value": "9003"
            },
            {
              "name": "NAME_NODE_HTTP_PORT",
              "value": "9002"
            },
            {
              "name": "NAME_NODE_RPC_PORT",
              "value": "9001"
            },
            {
              "name": "CONFIG_TEMPLATE_CORE_SITE",
              "value": "config-templates/core-site,hadoop-2.6.0-cdh5.9.1/etc/hadoop/core-site.xml"
            },
            {
              "name": "CONFIG_TEMPLATE_HDFS_SITE",
              "value": "config-templates/hdfs-site,hadoop-2.6.0-cdh5.9.1/etc/hadoop/hdfs-site.xml"
            },
            {
              "name": "CONFIG_TEMPLATE_HADOOP_METRICS2",
              "value": "config-templates/hadoop-metrics2,hadoop-2.6.0-cdh5.9.1/etc/hadoop/hadoop-metrics2.properties"
            },
            {
              "name": "PORT_JOURNAL_RPC",
              "value": "8485"
            },
            {
              "name": "PORT_JOURNAL_HTTP",
              "value": "8480"
            }
          ]
        },
        "shell": null,
        "value": "./bootstrap && ./hadoop-2.6.0-cdh5.9.1/bin/hdfs journalnode",
        "arguments": [],
        "user": null
      },
      "container": null,
      "healthCheck": null,
      "killPolicy": null,
      "data": null,
      "labels": {
        "labels": [
          {
            "key": "goal_state",
            "value": "RUNNING"
          },
          {
            "key": "offer_attributes",
            "value": ""
          },
          {
            "key": "task_type",
            "value": "journal"
          },
          {
            "key": "index",
            "value": "0"
          },
          {
            "key": "offer_hostname",
            "value": "10.0.1.23"
          },
          {
            "key": "target_configuration",
            "value": "4bdb3f97-96b0-4e78-8d47-f39edc33f6e3"
          }
        ]
      },
      "discovery": null
    },
    "status": {
      "taskId": {
        "value": "journal-0-node__b31a70f4-73c5-4065-990c-76c0c704b8e4"
      },
      "state": "TASK_RUNNING",
      "message": "Reconciliation: Latest task state",
      "source": "SOURCE_MASTER",
      "reason": "REASON_RECONCILIATION",
      "data": null,
      "slaveId": {
        "value": "0060634a-aa2b-4fcc-afa6-5569716b533a-S5"
      },
      "executorId": null,
      "timestamp": 1.486694618923135E9,
      "uuid": null,
      "healthy": null,
      "labels": null,
      "containerStatus": {
        "containerId": {
          "value": "a4c8433f-2648-4ba7-a8b8-5fe5df20e8af",
          "parent": null
        },
        "networkInfos": [
          {
            "ipAddresses": [
              {
                "protocol": null,
                "ipAddress": "10.0.1.23"
              }
            ],
            "name": null,
            "groups": [],
            "labels": null,
            "portMappings": []
          }
        ],
        "cgroupInfo": null,
        "executorPid": 5594
      },
      "unreachableTime": null
    }
  }
]
```

## Node Status
Similarly, the status for any node may also be queried.

```bash
$ dcos hdfs --name=<service-name> pods info <node-id>
```

For example:

```bash
$ dcos hdfs pods info journal-0
```

```json
[
  {
    "name": "journal-0-node",
    "id": "journal-0-node__b31a70f4-73c5-4065-990c-76c0c704b8e4",
    "state": "TASK_RUNNING"
  }
]
```
