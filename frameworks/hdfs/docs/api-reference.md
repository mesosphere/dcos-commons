---
post_title: API Reference
menu_order: 60
feature_maturity: experimental
enterprise: 'no'
---

The DC/OS HDFS Service implements a REST API that may be accessed from outside the cluster. The <dcos_url> parameter referenced below indicates the base URL of the DC/OS cluster on which the HDFS Service is deployed.

<a name="#rest-auth"></a>
# REST API Authentication
REST API requests must be authenticated. This authentication is only applicable for interacting with the HDFS REST API directly. You do not need the token to access the HDFS nodes themselves.
 
If you are using Enterprise DC/OS, follow these instructions to [create a service account and an authentication token](https://docs.mesosphere.com/1.9/administration/id-and-access-mgt/service-auth/custom-service-auth/). You can then configure your service to automatically refresh the authentication token when it expires. To get started more quickly, you can also [get the authentication token without a service account](https://docs.mesosphere.com/1.9/administration/id-and-access-mgt/iam-api/), but you will need to manually refresh the token.

If you are using open source DC/OS, follow these instructions to [pass your HTTP API token to the DC/OS endpoint](https://dcos.io/docs/1.9/administration/id-and-access-mgt/iam-api/). 

Once you have the authentication token, you can store it in an environment variable and reference it in your REST API calls:

```
$ export auth_token=uSeR_t0k3n
```

The `curl` examples in this document assume that an auth token has been stored in an environment variable named `auth_token`.

If you are using Enterprise DC/OS, the security mode of your installation may also require the `--ca-cert` flag when making REST calls. Refer to [Obtaining and passing the DC/OS certificate in cURL requests](https://docs.mesosphere.com/1.9/administration/tls-ssl/#get-dcos-cert) for information on how to use the `--cacert` flag. [If your security mode is `disabled`](https://docs.mesosphere.com/1.9/administration/tls-ssl/), do not use the `--ca-cert` flag.

# Plan API
The Plan API provides endpoints for monitoring and controlling service installation and configuration updates.

```bash
$ curl -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/plan
```
## Pause Installation

The installation will pause after completing installation of the current node and wait for user input.

```bash
$ curl -X POST -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/plan/interrupt
```

## Resume Installation

The REST API request below will resume installation at the next pending node.

```bash
$ curl -X PUT <dcos_surl>/service/hdfs/v1/plan/continue
```

# Connection API

```bash
$ curl -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/connect/hdfs-site.xml
```

You will see a response similar to the following:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?><configuration>
<property>
<name>dfs.nameservice.id</name>
<value>hdfs</value>
</property>
<property>
<name>dfs.nameservices</name>
<value>hdfs</value>
</property>
<property>
<name>dfs.ha.namenodes.hdfs</name>
<value>namenode-0,namenode-1</value>
</property>
<property>
<name>dfs.namenode.http-address.hdfs.namenode-0</name>
<value>namenode-0.hdfs.mesos:9002</value>
</property>
<property>
<name>dfs.namenode.rpc-bind-host.hdfs.namenode-1</name>
<value>0.0.0.0</value>
</property>
<property>
<name>dfs.namenode.http-address.hdfs.namenode-1</name>
<value>namenode-1.hdfs.mesos:9002</value>
</property>
<property>
<name>dfs.namenode.rpc-address.hdfs.namenode-0</name>
<value>namenode-0.hdfs.mesos:9001</value>
</property>
<property>
<name>dfs.namenode.rpc-bind-host.hdfs.namenode-0</name>
<value>0.0.0.0</value>
</property>
<property>
<name>dfs.namenode.http-bind-host.hdfs.namenode-1</name>
<value>0.0.0.0</value>
</property>
<property>
<name>dfs.namenode.http-bind-host.hdfs.namenode-0</name>
<value>0.0.0.0</value>
</property>
<property>
<name>dfs.namenode.rpc-address.hdfs.namenode-1</name>
<value>namenode-1.hdfs.mesos:9001</value>
</property>
<property>
<name>dfs.ha.automatic-failover.enabled</name>
<value>true</value>
</property>
<property>
<name>dfs.client.failover.proxy.provider.hdfs</name>
<value>org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider</value>
</property>
</configuration>
```
The contents of the response represent a valid hdfs-site.xml that can be used by clients to connect to the service.

# Nodes API

The nodes API provides endpoints for retrieving information about nodes, restarting them, and replacing them.

## List Nodes

A list of available node ids can be retrieved by sending a GET request to `/v1/state/tasks`:

CLI Example
```
$ dcos hdfs state tasks
```

HTTP Example
```
$ curl  -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/state/tasks

[
	"journalnode-0",
	"journalnode-1",
	"namenode-0",
	"datanode-2",
	"journalnode-2",
	"namenode-1",
	"datanode-1",
	"datanode-0"
]
```

## Node Info

You can retrieve node information by sending a GET request to `/v1/state/tasks/info/<node-id>`:

```
$ curl  -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/state/tasks/info/<node-#>
```

CLI Example
```
$ dcos hdfs state task journalnode-0
```

HTTP Example
```
$ curl  -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/state/tasks/info/journalnode-0

{
    "executor": {
        "command": {
            "environment": {
                "variables": [
                    ... long list ...
                ]
            },
            "uris": [
                {
                    "cache": false,
                    "executable": false,
                    "extract": true,
                    "value": "hadoop-2.6.0-cdh5.7.1-dcos.tar.gz"
                },
                {
                    "cache": false,
                    "executable": false,
                    "extract": true,
                    "value": "jre-8u91-linux-x64.tar.gz"
                },
                {
                    "cache": false,
                    "executable": false,
                    "extract": true,
                    "value": "executor.zip"
                }
            ],
            "value": "./executor/bin/hdfs-executor executor/conf/executor.yml"
        },
        "executor_id": {
            "value": "hdfs-executor-journalnode-0__9a485636-cd8e-4ace-aac7-63c86cdf7b09"
        },
        "framework_id": {
            "value": "f9ea7ec2-311b-420d-b05a-e00c6928b62f-0001"
        },
        "name": "hdfs-executor-journalnode-0",
        "resources": [
            {
                "name": "cpus",
                "reservation": {
                    "labels": {
                        "labels": [
                            {
                                "key": "resource_id",
                                "value": "ad52380f-5050-4df7-a560-44a61fbdda11"
                            }
                        ]
                    },
                    "principal": "hdfs-principal"
                },
                "role": "hdfs-role",
                "scalar": {
                    "value": 0.5
                },
                "type": "SCALAR"
            },
            {
                "name": "mem",
                "reservation": {
                    "labels": {
                        "labels": [
                            {
                                "key": "resource_id",
                                "value": "37921092-28f4-4656-82ba-d3f1b9bc9319"
                            }
                        ]
                    },
                    "principal": "hdfs-principal"
                },
                "role": "hdfs-role",
                "scalar": {
                    "value": 1024.0
                },
                "type": "SCALAR"
            },
            {
                "name": "disk",
                "reservation": {
                    "labels": {
                        "labels": [
                            {
                                "key": "resource_id",
                                "value": "29d9c38b-2a16-4ca4-99f6-2c86e043e949"
                            }
                        ]
                    },
                    "principal": "hdfs-principal"
                },
                "role": "hdfs-role",
                "scalar": {
                    "value": 1024.0
                },
                "type": "SCALAR"
            }
        ]
    },
    "labels": {
        "labels": [
            {
                "key": "hdfs_zkfc_formatted",
                "value": "true"
            },
            {
                "key": "hdfs_node_initialized",
                "value": "true"
            },
            {
                "key": "hdfs_task_type",
                "value": "JOURNAL_NODE"
            },
            {
                "key": "hdfs_fs_formatted",
                "value": "true"
            },
            {
                "key": "hdfs_heap_mb",
                "value": "2048"
            },
            {
                "key": "hdfs_upgrade",
                "value": "false"
            },
            {
                "key": "hdfs_standby",
                "value": "false"
            },
            {
                "key": "hdfs_task_ports",
                "value": "8480,8485"
            },
            {
                "key": "hdfs_rollback",
                "value": "false"
            },
            {
                "key": "hdfs_disk_type",
                "value": "ROOT"
            },
            {
                "key": "hdfs_task_hostname",
                "value": "10.0.1.3"
            },
            {
                "key": "hdfs_firstlaunch",
                "value": "false"
            },
            {
                "key": "hdfs_task_state",
                "value": "TASK_RUNNING"
            }
        ]
    },
    "name": "journalnode-0",
    "resources": [
        {
            "name": "cpus",
            "reservation": {
                "labels": {
                    "labels": [
                        {
                            "key": "resource_id",
                            "value": "f921af86-2780-47c5-a8ef-ab09ba2d3a54"
                        }
                    ]
                },
                "principal": "hdfs-principal"
            },
            "role": "hdfs-role",
            "scalar": {
                "value": 0.5
            },
            "type": "SCALAR"
        },
        {
            "name": "mem",
            "reservation": {
                "labels": {
                    "labels": [
                        {
                            "key": "resource_id",
                            "value": "b9257f7d-3ae0-4f44-a6fa-2baf745cdc18"
                        }
                    ]
                },
                "principal": "hdfs-principal"
            },
            "role": "hdfs-role",
            "scalar": {
                "value": 2048.0
            },
            "type": "SCALAR"
        },
        {
            "disk": {
                "persistence": {
                    "id": "f64da742-962e-4b22-bde3-eab4d2802d1e",
                    "principal": "hdfs-principal"
                },
                "volume": {
                    "container_path": "volume",
                    "mode": "RW"
                }
            },
            "name": "disk",
            "reservation": {
                "labels": {
                    "labels": [
                        {
                            "key": "resource_id",
                            "value": "8ef21eab-d491-4857-9ddb-76fde0b2412b"
                        }
                    ]
                },
                "principal": "hdfs-principal"
            },
            "role": "hdfs-role",
            "scalar": {
                "value": 10240.0
            },
            "type": "SCALAR"
        },
        {
            "name": "ports",
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
            "reservation": {
                "labels": {
                    "labels": [
                        {
                            "key": "resource_id",
                            "value": "b4943d73-81f2-46b8-8d95-c39bb319b9bd"
                        }
                    ]
                },
                "principal": "hdfs-principal"
            },
            "role": "hdfs-role",
            "type": "RANGES"
        }
    ],
    "slave_id": {
        "value": "f9ea7ec2-311b-420d-b05a-e00c6928b62f-S3"
    },
    "task_id": {
        "value": "journalnode-0__03bff4cf-b5ab-4484-96fc-df36e1ad985b"
    }
}
```

## Replace a Node

The replace endpoint can be used to replace a node with an instance running on another agent node.

CLI Example
```
$ dcos hdfs node replace <node-id>
```

HTTP Example
```
$ curl  -X PUT -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/nodes/replace?node=<node-id>
```

If the operation succeeds, a `200 OK` is returned.

## Restart a Node

The restart endpoint can be used to restart a node in place on the same agent node.

CLI Example
```
$ dcos hdfs node restart <node-id>
```

HTTP Example
```bash
$ curl  -X PUT -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/nodes/restart?node=<node-id>
```

If the operation succeeds a `200 OK` is returned.

# Configuration API

The configuration API provides an endpoint to view current and previous configurations of the cluster.

## View Target Config

You can view the current target configuration by sending a GET request to `/v1/configurations/target`.

CLI Example
```
$ dcos hdfs config target
```

HTTP Example
```
$ curl -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/configurations/target
{
    "core": {
        "default_name": "hdfs://hdfs",
        "http_fs_groups": "*",
        "http_fs_hosts": "*",
        "hue_groups": "*",
        "hue_hosts": "*",
        "root_groups": "*",
        "root_hosts": "*"
    },
    "curator": "master.mesos:2181",
    "dataNode": {
        "cpus": 0.5,
        "disk_mb": 10240,
        "disk_type": "ROOT",
        "heap_mb": 2048,
        "memory_mb": 4096
    },
    "dataNodesCount": 3,
    "executor": {
        "command": "./executor/bin/hdfs-executor executor/conf/executor.yml",
        "cpus": 0.5,
        "disk_mb": 1024,
        "executor_url": "executor.zip",
        "hdfs_home": "./hadoop-2.6.0-cdh5.7.1",
        "hdfs_url": "hadoop-2.6.0-cdh5.7.1-dcos.tar.gz",
        "hdfs_version": "2.5.0",
        "heap_mb": 768,
        "java_home": "./jre1.8.0_91",
        "jre_url": "jre-8u91-linux-x64.tar.gz",
        "memory_mb": 1024
    },
    "hdfs": {
        "client_read_short_circuit": true,
        "client_read_short_circuit_cache_expiry_ms": 1000,
        "client_read_short_circuit_streams": 1000,
        "compress_image": true,
        "data_node_address": "0.0.0.0",
        "data_node_bandwidth_per_second": 41943040,
        "data_node_handler_count": 10,
        "data_node_http_port": 9004,
        "data_node_ipc_port": 9005,
        "data_node_rpc_port": 9003,
        "domain_socket_directory": "",
        "image_compression_codec": "org.apache.hadoop.io.compress.SnappyCodec",
        "journal_node_address": "0.0.0.0",
        "journal_node_http_port": 8480,
        "journal_node_rpc_port": 8485,
        "journal_nodes": 3,
        "name_node_bind_host": "0.0.0.0",
        "name_node_handler_count": 20,
        "name_node_heartbeat_recheck_interval": 60000,
        "name_node_http_port": 9002,
        "name_node_invalidate_work_percentage": 0.95,
        "name_node_replication_work_multiplier": 4,
        "name_node_rpc_port": 9001,
        "name_node_threshold_percentage": 0.9,
        "permissions_enabled": false,
        "service_name": "hdfs",
        "volume_directory": "volume",
        "zookeeper_quorum": "master.mesos:2181"
    },
    "journalNode": {
        "cpus": 0.5,
        "disk_mb": 10240,
        "disk_type": "ROOT",
        "heap_mb": 2048,
        "memory_mb": 2048
    },
    "nameNode": {
        "cpus": 0.5,
        "disk_mb": 10240,
        "disk_type": "ROOT",
        "heap_mb": 2048,
        "memory_mb": 4096
    },
    "service": {
        "checkpoint": true,
        "name": "hdfs",
        "principal": "hdfs-principal",
        "role": "hdfs-role",
        "secret": "",
        "user": "nobody"
    }
}
```

## List Configs

You can list all configuration IDs by sending a GET request to `/v1/configurations`.

CLI Example
```
$ dcos hdfs config list
```

HTTP Example
```
$ curl -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/configurations
[
    "9a8d4308-ab9d-4121-b460-696ec3368ad6"
]
```

## View Specified Config

You can view a specific configuration by sending a GET request to `/v1/configurations/<config-id>`.

CLI Example
```
$ dcos hdfs config show 9a8d4308-ab9d-4121-b460-696ec3368ad6
```

HTTP Example
```
$ curl -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/configurations/9a8d4308-ab9d-4121-b460-696ec3368ad6
{
    ... same format as target config above ...
}
```

# Service Health API

The service health endpoint provides health checks for the services.

HTTP Example
```
$ curl -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/admin/healthcheck
{
	"data_nodes": {
		"healthy": true,
		"message": "No failed Data Nodes."
	},
	"deadlocks": {
		"healthy": true
	},
	"journal_nodes": {
		"healthy": true,
		"message": "No failed Journal Nodes."
	},
	"name_nodes": {
		"healthy": true,
		"message": "No failed Name Nodes."
	}
}
```
