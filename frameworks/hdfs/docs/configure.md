---
post_title: Configuring
menu_order: 30
feature_maturity: experimental
enterprise: 'no'
---

# Changing Configuration at Runtime

You can customize your cluster in-place when it is up and running.

The HDFS scheduler runs as a Marathon process and can be reconfigured by changing values for the service from the DC/OS dashboard. These are the general steps to follow:

1. Go to the DC/OS dashboard.
1. Click the **Services** tab, then the name of the HDFS service to be updated.
1. Within the HDFS instance details view, click the menu in the upper right, then choose **Edit**.
1. click the menu in the upper right, then choose **Edit**. For example, to increase the number of nodes, edit the value for `DATA_NODES`.
1. Click **REVIEW & RUN** to apply any changes and cleanly reload the HDFS scheduler. The HDFS cluster itself will persist across the change.

## Configuration Deployment Strategy

Configuration updates are rolled out through execution of update plans. You can configure the way these plans are executed.

### Configuration Update Plans

This configuration update strategy is analogous to the installation procedure above. If the configuration update is accepted, there will be no errors in the generated plan, and a rolling restart will be performed on all nodes to apply the updated configuration. However, the default strategy can be overridden by a strategy the user provides.

# Configuration Update

Make the REST request below to view the current plan. See the REST API Authentication part of the REST API Reference section for information on how this request must be authenticated.

```
$ curl -v -H "Authorization: token=$(dcos config show core.dcos_acs_token)" http://<dcos_url>/service/hdfs/v1/plan
```

The response will look similar to this:

```
{
	"phases": [{
		"id": "fd33f9cb-88ba-4db6-9b8b-749bb1f63308",
		"name": "Reconciliation",
		"blocks": [{
			"id": "878859fc-5985-4fc9-8517-0b184485e705",
			"status": "Complete",
			"name": "Reconciliation",
			"message": "Reconciliation complete",
			"has_decision_point": false
		}],
		"status": "Complete"
	}, {
		"id": "15aa294a-6698-45ef-9695-95f1e8fcc47b",
		"name": "Quorum Journal",
		"blocks": [{
			"id": "23c46589-994e-495c-993a-f192a9d26f52",
			"status": "Complete",
			"name": "journalnode-0",
			"message": "journalnode-0 in target state.",
			"has_decision_point": false
		}, {
			"id": "c234f3aa-5845-4dba-8036-f191b87dbe6d",
			"status": "Complete",
			"name": "journalnode-1",
			"message": "journalnode-1 in target state.",
			"has_decision_point": false
		}, {
			"id": "87b9d739-a48d-473c-ad83-1dec8b5a7661",
			"status": "Complete",
			"name": "journalnode-2",
			"message": "journalnode-2 in target state.",
			"has_decision_point": false
		}],
		"status": "Complete"
	}, {
		"id": "21169e1a-3488-4738-9e6e-feabcb04df1f",
		"name": "Name Service",
		"blocks": [{
			"id": "4525fd99-6ebc-4e8d-90de-0a10d55a36ac",
			"status": "Complete",
			"name": "namenode-0",
			"message": "namenode-0 in target state.",
			"has_decision_point": false
		}, {
			"id": "12801661-b15c-4bb5-8d28-16cd00270610",
			"status": "InProgress",
			"name": "namenode-1",
			"message": "namenode-1 in target state.",
			"has_decision_point": false
		}],
		"status": "InProgress"
	}, {
		"id": "790e4b9e-7b1d-4bb6-871d-fa185eec8789",
		"name": "Distributed Storage",
		"blocks": [{
			"id": "487aaaa8-a351-45b6-9471-17ff7f07f62c",
			"status": "Pending",
			"name": "datanode-0",
			"message": "datanode-0 in target state.",
			"has_decision_point": false
		}, {
			"id": "d52ff01f-2bda-47b7-9551-89828d7d717d",
			"status": "Pending",
			"name": "datanode-1",
			"message": "datanode-1 in target state.",
			"has_decision_point": false
		}, {
			"id": "930f89bd-aa1e-4d9f-b8f6-74e765a989b3",
			"status": "Complete",
			"name": "datanode-2",
			"message": "datanode-2 in target state.",
			"has_decision_point": false
		}],
		"status": "Pending"
	}],
	"errors": [],
	"status": "InProgress"
}
```

If you want to interrupt a configuration update that is in progress, enter the `interrupt` command.

```
$ curl -X -H "Authorization: token=$(dcos config show core.dcos_acs_token)" POST http:/<dcos_url>/service/hdfs/v1/plan/interrupt
```


If you query the plan again, the response will look like this (notice `status: "Waiting"`):

```
{
	"phases": [{
		"id": "fd33f9cb-88ba-4db6-9b8b-749bb1f63308",
		"name": "Reconciliation",
		"blocks": [{
			"id": "878859fc-5985-4fc9-8517-0b184485e705",
			"status": "Complete",
			"name": "Reconciliation",
			"message": "Reconciliation complete",
			"has_decision_point": false
		}],
		"status": "Complete"
	}, {
		"id": "15aa294a-6698-45ef-9695-95f1e8fcc47b",
		"name": "Quorum Journal",
		"blocks": [{
			"id": "23c46589-994e-495c-993a-f192a9d26f52",
			"status": "Complete",
			"name": "journalnode-0",
			"message": "journalnode-0 in target state.",
			"has_decision_point": false
		}, {
			"id": "c234f3aa-5845-4dba-8036-f191b87dbe6d",
			"status": "Complete",
			"name": "journalnode-1",
			"message": "journalnode-1 in target state.",
			"has_decision_point": false
		}, {
			"id": "87b9d739-a48d-473c-ad83-1dec8b5a7661",
			"status": "Complete",
			"name": "journalnode-2",
			"message": "journalnode-2 in target state.",
			"has_decision_point": false
		}],
		"status": "Complete"
	}, {
		"id": "21169e1a-3488-4738-9e6e-feabcb04df1f",
		"name": "Name Service",
		"blocks": [{
			"id": "4525fd99-6ebc-4e8d-90de-0a10d55a36ac",
			"status": "Complete",
			"name": "namenode-0",
			"message": "namenode-0 in target state.",
			"has_decision_point": false
		}, {
			"id": "12801661-b15c-4bb5-8d28-16cd00270610",
			"status": "InProgress",
			"name": "namenode-1",
			"message": "namenode-1 in target state.",
			"has_decision_point": false
		}],
		"status": "InProgress"
	}, {
		"id": "790e4b9e-7b1d-4bb6-871d-fa185eec8789",
		"name": "Distributed Storage",
		"blocks": [{
			"id": "487aaaa8-a351-45b6-9471-17ff7f07f62c",
			"status": "Waiting",
			"name": "datanode-0",
			"message": "datanode-0 in target state.",
			"has_decision_point": false
		}, {
			"id": "d52ff01f-2bda-47b7-9551-89828d7d717d",
			"status": "Pending",
			"name": "datanode-1",
			"message": "datanode-1 in target state.",
			"has_decision_point": false
		}, {
			"id": "930f89bd-aa1e-4d9f-b8f6-74e765a989b3",
			"status": "Pending",
			"name": "datanode-2",
			"message": "datanode-2 in target state.",
			"has_decision_point": false
		}],
		"status": "Pending"
	}],
	"errors": [],
	"status": "InProgress"
}
```

**Note:** The interrupt command can’t stop a block that is `InProgress`, but it will stop the change on the subsequent blocks.

Enter the `continue` command to resume the update process.

```
$ curl -X -H "Authorization: token=$(dcos config show core.dcos_acs_token)" POST http://<dcos_url>/service/hdfs/v1/plan/continue
```

After you execute the continue operation, the plan will look like this:

```
{
	"phases": [{
		"id": "fd33f9cb-88ba-4db6-9b8b-749bb1f63308",
		"name": "Reconciliation",
		"blocks": [{
			"id": "878859fc-5985-4fc9-8517-0b184485e705",
			"status": "Complete",
			"name": "Reconciliation",
			"message": "Reconciliation complete",
			"has_decision_point": false
		}],
		"status": "Complete"
	}, {
		"id": "15aa294a-6698-45ef-9695-95f1e8fcc47b",
		"name": "Quorum Journal",
		"blocks": [{
			"id": "23c46589-994e-495c-993a-f192a9d26f52",
			"status": "Complete",
			"name": "journalnode-0",
			"message": "journalnode-0 in target state.",
			"has_decision_point": false
		}, {
			"id": "c234f3aa-5845-4dba-8036-f191b87dbe6d",
			"status": "Complete",
			"name": "journalnode-1",
			"message": "journalnode-1 in target state.",
			"has_decision_point": false
		}, {
			"id": "87b9d739-a48d-473c-ad83-1dec8b5a7661",
			"status": "Complete",
			"name": "journalnode-2",
			"message": "journalnode-2 in target state.",
			"has_decision_point": false
		}],
		"status": "Complete"
	}, {
		"id": "21169e1a-3488-4738-9e6e-feabcb04df1f",
		"name": "Name Service",
		"blocks": [{
			"id": "4525fd99-6ebc-4e8d-90de-0a10d55a36ac",
			"status": "Complete",
			"name": "namenode-0",
			"message": "namenode-0 in target state.",
			"has_decision_point": false
		}, {
			"id": "12801661-b15c-4bb5-8d28-16cd00270610",
			"status": "InProgress",
			"name": "namenode-1",
			"message": "namenode-1 in target state.",
			"has_decision_point": false
		}],
		"status": "Complete"
	}, {
		"id": "790e4b9e-7b1d-4bb6-871d-fa185eec8789",
		"name": "Distributed Storage",
		"blocks": [{
			"id": "487aaaa8-a351-45b6-9471-17ff7f07f62c",
			"status": "InProgress",
			"name": "datanode-0",
			"message": "datanode-0 in target state.",
			"has_decision_point": false
		}, {
			"id": "d52ff01f-2bda-47b7-9551-89828d7d717d",
			"status": "Pending",
			"name": "datanode-1",
			"message": "datanode-1 in target state.",
			"has_decision_point": false
		}, {
			"id": "930f89bd-aa1e-4d9f-b8f6-74e765a989b3",
			"status": "Pending",
			"name": "datanode-2",
			"message": "datanode-2 in target state.",
			"has_decision_point": false
		}],
		"status": "InProgress"
	}],
	"errors": [],
	"status": "InProgress"
}
```

# Configuration Options

The following describes the most commonly used features of DC/OS Apache Cassandra and how to configure them via the DC/OS CLI and the DC/OS GUI. There are two methods of configuring a HDFS cluster. The configuration may be specified using a JSON file during installation via the DC/OS command line (See the Installation section) or via modification to the Service Scheduler’s DC/OS environment at runtime (See the Configuration Update section). Note that some configuration options may only be specified at installation time.

## Service Configuration

The service configuration object contains properties that MUST be specified during installation and CANNOT be modified after installation is in progress. This configuration object is similar across all DC/OS Infinity services. Service configuration example:

```
{
    "service": {
        "name": "hdfs",
        "role": "hdfs_role",
        "principal": "hdfs_principal",
        "secret" : "/path/to/secret_file",
        "cpus" : 0.5,
        "mem" : 2048,
        "heap" : 1024,
        "api_port" : 9000
    }
}
```

<table class="table">
  <tr>
    <th>Property</th>
    <th>Type</th>
    <th>Description</th>
  </tr>

  <tr>
    <td>name</td>
    <td>string</td>
    <td>The name of the HDFS service installation. This must be unique for each DC/OS HDFS service instance deployed on a DC/OS cluster. It will determine the ID of the HDFS nameservice, which must be unique within a DC/OS cluster.</td>
  </tr>

  <tr>
    <td>role</td>
    <td>string</td>
    <td>The role of the HDFS service in the DC/OS cluster.</td>
  </tr>

  <tr>
    <td>principal</td>
    <td>string</td>
    <td>The authentication principal for the HDFS cluster.</td>
  </tr>


  <tr>
    <td>secret</td>
    <td>string</td>
    <td>An optional path to the file containing the secret that the service will use to authenticate with the Mesos master in the DC/OS cluster. This parameter is optional, and should be omitted unless the DC/OS deployment is specifically configured for authentication.</td>
  </tr>

   <tr>
      <td>cpus</td>
      <td>number</td>
      <td>The number of CPU shares allocated to the HDFS Service scheduler.</td>
    </tr>

    <tr>
      <td>mem</td>
      <td>integer</td>
      <td>The amount of memory, in MB, allocated for the HDFS Service scheduler. This MUST be larger than the allocated heap. 2 Gb is a good choice.</td>
    </tr>

    <tr>
      <td>heap</td>
      <td>integer</td>
      <td>The amount of heap, in MB, allocated for the HDFS Service scheduler. 1 Gb is a minimum for production installations.</td>
    </tr>

    <tr>
      <td>api_port</td>
      <td>integer</td>
      <td>The port that the scheduler will accept API requests on.</td>
    </tr>

</table>

## Change the Service Name

- **In the DC/OS CLI, options.json**: `name` = string (default: `hdfs`)

## Node Configuration

The node configuration objects correspond to the configuration for nodes in the HDFS cluster. Node configuration MUST be specified during installation and MAY be modified during configuration updates. All of the properties except `disk` and `disk_type` MAY be modified during the configuration update process.

Example node configuration:
```
    "name_node": {
		"cpus": 0.5,
		"memory_mb": 4096,
		"heap_mb": 2048,
		"disk_mb": 10240,
		"disk_type": "ROOT"
	},
	"journal_node": {
		"cpus": 0.5,
		"memory_mb": 4096,
		"heap_mb": 2048,
		"disk_mb": 10240,
		"disk_type": "ROOT"
		"count" : 3
	},
	"data_node": {
		"cpus": 0.5,
		"memory_mb": 4096,
		"heap_mb": 2048,
		"disk_mb": 10240,
		"disk_type": "ROOT"
		"count" : 3
	}
```

<table class="table">

  <tr>
    <th>Property</th>
    <th>Type</th>
    <th>Description</th>
  </tr>

   <tr>
    <td>cpus</td>
    <td>number</td>
    <td>The number of cpu shares allocated to the node's process.</td>
  </tr>

  <tr>
    <td>mem</td>
    <td>integer</td>
    <td>The amount of memory, in MB, allocated to the node. This value MUST be larger than the specified max heap size. Make sure to allocate enough space for additional memory used by the JVM and other overhead. A good rule of thumb is allocate twice the heap size in MB for memory.</td>
  </tr>

  <tr>
    <td>disk</td>
    <td>integer</td>
    <td>The amount of disk, in MB, allocated to node. **Note:** Once this value is configured, it can not be changed.</td>
  </tr>

  <tr>
    <td>disk_type</td>
    <td>string</td>
    <td>The type of disk to use for storing data. Possible values: <b>ROOT</b> (default) and <b>MOUNT</b>. <b>Note:</b> Once this value is configured, it can not be changed.
    <ul>
    <li><b>ROOT:</b> Data is stored on the same volume as the agent work directory and the node tasks use the configured amount of <i>disk</i> space.</li>
    <li><b>MOUNT:</b> Data will be stored on a dedicated, operator-formatted volume attached to the agent. Dedicated MOUNT volumes have performance advantages and a disk error on these MOUNT volumes will be correctly reported to HDFS.</li>
    </ul>
    </td>
  </tr>

  <tr>
    <td>heap</td>
    <td>integer</td>
    <td>The maximum and minimum heap size used by the node process in MB. This value SHOULD be at least 4 GB for all node types, and it SHOULD be no larger than 80% of the allocated memory for the container.
  </tr>
  <tr>
    <td>count</td>
    <td>integer</td>
    <td>The number of nodes of that node type for the cluster. There are always exactly two name nodes, so the name_node object has no count property. Users may select either 3 or 5 journal nodes. The default value of 3 is sufficient for most deployments and should only be overridden after careful thought. At least 3 data nodes should be configured, but this value may be increased to meet the storage needs of the deployment.</td>
  </tr>
</table>

## Executor Configuration
The executor configuration object allows you to modify the resources associated with the DC/OS HDFS Service's executor. These properties should not be modified unless you are installing a small cluster in a resource-constrained environment.

Example executor configuration:
```json
{
    "executor": {
        "cpus": 0.5,
        "mem": 1024,
        "heap" : 768,
        "disk": 1024
    }
}
```

<table class="table">
    <tr>
    <th>Property</th>
    <th>Type</th>
    <th>Description</th>
  </tr>
   <tr>
      <td>cpus</td>
      <td>number</td>
      <td>The number of CPU shares allocated to the HDFS Service executor. </td>
    </tr>

    <tr>
      <td>mem</td>
      <td>integer</td>
      <td>The amount of memory, in MB, allocated for the HDFS Service executor. This MUST be larger than the allocated heap.</td>
    </tr>

    <tr>
      <td>heap</td>
      <td>integer</td>
      <td>The amount of heap, in MB, allocated for the HDFS Service executor.</td>
    </tr>

    <tr>
      <td>disk</td>
      <td>integer</td>
      <td>The amount of disk, in MB, allocated for the HDFS Service executor.</td>
    </tr>

</table>

## HDFS File System Configuration

The HDFS file system network configuration, permissions, and compression is configured via the `hdfs` JSON object. Once these properties are set at installation time they can not be reconfigured.
Example HDFS configuration:

```
{
    "hdfs": {
		"name_node_rpc_port": 9001,
		"name_node_http_port": 9002,
		"journal_node_rpc_port": 8485,
		"journal_node_http_port": 8480,
		"data_node_rpc_port": 9005,
		"data_node_http_port": 9006,
		"data_node_ipc_port": 9007,
		"permissions_enabled": false,
		"name_node_heartbeat_recheck_interval": 60000,
		"compress_image": false,
		"image_compression_codec": "org.apache.hadoop.io.compress.SnappyCodec"
   }
}
```

<table class="table">

  <tr>
    <th>Property</th>
    <th>Type</th>
    <th>Description</th>
  </tr>

   <tr>
    <td>name_node_rpc_port</td>
    <td>integer</td>
    <td>The port on which the name nodes will listen for RPC connections.</td>
  </tr>

   <tr>
    <td>name_node_http_port</td>
    <td>integer</td>
    <td>The port on which the name nodes will listen for HTTP connections.</td>
  </tr>

   <tr>
    <td>journal_node_rpc_port</td>
    <td>integer</td>
    <td>The port on which the journal nodes will listen for RPC connections.</td>
  </tr>

   <tr>
    <td>journal_node_http_port</td>
    <td>integer</td>
    <td>The port on which the journal nodes will listen for HTTP connections.</td>
  </tr>

   <tr>
    <td>data_node_rpc_port</td>
    <td>integer</td>
    <td>The port on which the data nodes will listen for RPC connections.</td>
  </tr>

   <tr>
    <td>data_node_http_port</td>
    <td>integer</td>
    <td>The port on which the data nodes will listen for HTTP connections.</td>
  </tr>

   <tr>
    <td>data_node_ipc_port</td>
    <td>integer</td>
    <td>The port on which the data nodes will listen for IPC connections. This property is useful if you deploy a service that colocates with HDFS data nodes. It provides domain socket communication instead of RPC</td>
  </tr>
</table>

# Operating System Configuration
In order for HDFS to function correctly, you must perform several important configuration modifications to the OS hosting the deployment.

## Configuration Settings
HDFS requires OS-level configuration settings typical of a production storage server.

<table class="table">

  <tr>
    <th>File</th>
    <th>Setting</th>
    <th>Value</th>
    <th>Reason</th>
  </tr>

   <tr>
    <td>/etc/sysctl.conf</td>
    <td>vm.swappiness</td>
    <td>0</td>
    <td>If the OS swaps out the HDFS processes, they can fail to respond to RPC requests, resulting in the process being marked down by the cluster. This can be particularly troublesome for name nodes and journal nodes.</td>
  </tr>

  <tr>
    <td>/etc/security/limits.conf</td>
    <td>nofile</td>
    <td>unlimited</td>
    <td>If this value is too low, a job that operate on the HDFS cluster may fail due to too may open file handles.</td>
  </tr>

  <tr>
    <td>/etc/security/limits.conf, /etc/security/limits.d/90-nproc.conf</td>
    <td>nproc</td>
    <td>32768</td>
    <td>An HDFS node spawns many threads, which go towards kernel nproc count. If nproc is not set appropriately, the node will be killed.</td>
  </tr>

</table>
