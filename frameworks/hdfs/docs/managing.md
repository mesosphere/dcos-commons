---
post_title: Managing
menu_order: 50
feature_maturity: preview
enterprise: 'no'
---

# Updating Configuration

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
$ curl -v -H "Authorization: token=$(dcos config show core.dcos_acs_token)" "http://<dcos_url>/service/hdfs/v1/plans/deploy"
```

The response will look similar to this:

```
{
	phases: [{
		id: "77708b6f-52db-4361-a56f-4d2bd9d6bf09",
		name: "jn-deploy",
		steps: [{
			id: "fe96b235-bf26-4b36-ad0e-8dd3494b5b63",
			status: "COMPLETE",
			name: "journal-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'journal-0:[node] [fe96b235-bf26-4b36-ad0e-8dd3494b5b63]' has status: 'COMPLETE'."
		}, {
			id: "3a590b92-f2e8-439a-8951-4135b6c29b34",
			status: "COMPLETE",
			name: "journal-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'journal-1:[node] [3a590b92-f2e8-439a-8951-4135b6c29b34]' has status: 'COMPLETE'."
		}, {
			id: "c079bfcc-b620-4e0b-93d5-604223c0538d",
			status: "COMPLETE",
			name: "journal-2:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'journal-2:[node] [c079bfcc-b620-4e0b-93d5-604223c0538d]' has status: 'COMPLETE'."
		}],
		status: "COMPLETE"
	}, {
		id: "61a822e4-421d-4a46-b374-b0abaac6d2d4",
		name: "nn-deploy",
		steps: [{
			id: "0361e2f5-6e5d-42c8-9696-420f38ba5398",
			status: "COMPLETE",
			name: "name-0:[format]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-0:[format] [0361e2f5-6e5d-42c8-9696-420f38ba5398]' has status: 'COMPLETE'."
		}, {
			id: "a3a07cd4-828d-4161-b607-a745f3845abf",
			status: "COMPLETE",
			name: "name-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-0:[node] [a3a07cd4-828d-4161-b607-a745f3845abf]' has status: 'COMPLETE'."
		}, {
			id: "74ce8cf4-63cf-4ba2-ae6d-3ca6a389c66d",
			status: "COMPLETE",
			name: "name-1:[bootstrap]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-1:[bootstrap] [74ce8cf4-63cf-4ba2-ae6d-3ca6a389c66d]' has status: 'COMPLETE'."
		}, {
			id: "d46eb578-eb55-40b7-a683-7e44543ce63e",
			status: "COMPLETE",
			name: "name-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-1:[node] [d46eb578-eb55-40b7-a683-7e44543ce63e]' has status: 'COMPLETE'."
		}],
		status: "COMPLETE"
	}, {
		id: "f42c610b-e52b-4ce9-ab1c-887a123df234",
		name: "zkfc-deploy",
		steps: [{
			id: "9a5be25e-9997-4be9-bc67-1722056c8e8a",
			status: "COMPLETE",
			name: "zkfc-0:[format]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'zkfc-0:[format] [9a5be25e-9997-4be9-bc67-1722056c8e8a]' has status: 'COMPLETE'."
		}, {
			id: "2c8c9518-6d3c-48ed-bca1-58fd749da9c0",
			status: "COMPLETE",
			name: "zkfc-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'zkfc-0:[node] [2c8c9518-6d3c-48ed-bca1-58fd749da9c0]' has status: 'COMPLETE'."
		}, {
			id: "7e146767-21be-4d95-b83d-667a647b0503",
			status: "COMPLETE",
			name: "zkfc-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'zkfc-1:[node] [7e146767-21be-4d95-b83d-667a647b0503]' has status: 'COMPLETE'."
		}],
		status: "COMPLETE"
	}, {
		id: "774c5fec-7195-4ffd-850f-df62cf629fa9",
		name: "dn-deploy",
		steps: [{
			id: "f41b364f-5804-41ec-9d02-cd27f7b484ef",
			status: "COMPLETE",
			name: "data-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'data-0:[node] [f41b364f-5804-41ec-9d02-cd27f7b484ef]' has status: 'COMPLETE'."
		}, {
			id: "22d457dc-6ad4-4f6f-93f3-4c5071069503",
			status: "STARTING",
			name: "data-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'data-1:[node] [22d457dc-6ad4-4f6f-93f3-4c5071069503]' has status: 'STARTING'."
		}, {
			id: "a2798e72-83e2-4fad-a673-c5ff42ac9a0c",
			status: "STARTING",
			name: "data-2:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'data-2:[node] [a2798e72-83e2-4fad-a673-c5ff42ac9a0c]' has status: 'STARTING'."
		}],
		status: "STARTING"
	}],
	errors: [],
	status: "STARTING"
}
```

If you want to interrupt a configuration update that is in progress, enter the `interrupt` command.

```
$ curl -X -H "Authorization: token=$(dcos config show core.dcos_acs_token)" POST http:/<dcos_url>/service/hdfs/v1/plans/deploy/interrupt
```


If you query the plan again, the response will look like this (notice `status: "Waiting"`):

```
{
	phases: [{
		id: "77708b6f-52db-4361-a56f-4d2bd9d6bf09",
		name: "jn-deploy",
		steps: [{
			id: "fe96b235-bf26-4b36-ad0e-8dd3494b5b63",
			status: "COMPLETE",
			name: "journal-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'journal-0:[node] [fe96b235-bf26-4b36-ad0e-8dd3494b5b63]' has status: 'COMPLETE'."
		}, {
			id: "3a590b92-f2e8-439a-8951-4135b6c29b34",
			status: "COMPLETE",
			name: "journal-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'journal-1:[node] [3a590b92-f2e8-439a-8951-4135b6c29b34]' has status: 'COMPLETE'."
		}, {
			id: "c079bfcc-b620-4e0b-93d5-604223c0538d",
			status: "COMPLETE",
			name: "journal-2:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'journal-2:[node] [c079bfcc-b620-4e0b-93d5-604223c0538d]' has status: 'COMPLETE'."
		}],
		status: "COMPLETE"
	}, {
		id: "61a822e4-421d-4a46-b374-b0abaac6d2d4",
		name: "nn-deploy",
		steps: [{
			id: "0361e2f5-6e5d-42c8-9696-420f38ba5398",
			status: "COMPLETE",
			name: "name-0:[format]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-0:[format] [0361e2f5-6e5d-42c8-9696-420f38ba5398]' has status: 'COMPLETE'."
		}, {
			id: "a3a07cd4-828d-4161-b607-a745f3845abf",
			status: "COMPLETE",
			name: "name-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-0:[node] [a3a07cd4-828d-4161-b607-a745f3845abf]' has status: 'COMPLETE'."
		}, {
			id: "74ce8cf4-63cf-4ba2-ae6d-3ca6a389c66d",
			status: "COMPLETE",
			name: "name-1:[bootstrap]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-1:[bootstrap] [74ce8cf4-63cf-4ba2-ae6d-3ca6a389c66d]' has status: 'COMPLETE'."
		}, {
			id: "d46eb578-eb55-40b7-a683-7e44543ce63e",
			status: "COMPLETE",
			name: "name-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-1:[node] [d46eb578-eb55-40b7-a683-7e44543ce63e]' has status: 'COMPLETE'."
		}],
		status: "COMPLETE"
	}, {
		id: "f42c610b-e52b-4ce9-ab1c-887a123df234",
		name: "zkfc-deploy",
		steps: [{
			id: "9a5be25e-9997-4be9-bc67-1722056c8e8a",
			status: "COMPLETE",
			name: "zkfc-0:[format]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'zkfc-0:[format] [9a5be25e-9997-4be9-bc67-1722056c8e8a]' has status: 'COMPLETE'."
		}, {
			id: "2c8c9518-6d3c-48ed-bca1-58fd749da9c0",
			status: "COMPLETE",
			name: "zkfc-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'zkfc-0:[node] [2c8c9518-6d3c-48ed-bca1-58fd749da9c0]' has status: 'COMPLETE'."
		}, {
			id: "7e146767-21be-4d95-b83d-667a647b0503",
			status: "COMPLETE",
			name: "zkfc-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'zkfc-1:[node] [7e146767-21be-4d95-b83d-667a647b0503]' has status: 'COMPLETE'."
		}],
		status: "COMPLETE"
	}, {
		id: "774c5fec-7195-4ffd-850f-df62cf629fa9",
		name: "dn-deploy",
		steps: [{
			id: "f41b364f-5804-41ec-9d02-cd27f7b484ef",
			status: "COMPLETE",
			name: "data-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'data-0:[node] [f41b364f-5804-41ec-9d02-cd27f7b484ef]' has status: 'COMPLETE'."
		}, {
			id: "22d457dc-6ad4-4f6f-93f3-4c5071069503",
			status: "STARTING",
			name: "data-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'data-1:[node] [22d457dc-6ad4-4f6f-93f3-4c5071069503]' has status: 'STARTING'."
		}, {
			id: "a2798e72-83e2-4fad-a673-c5ff42ac9a0c",
			status: "PENDING",
			name: "data-2:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'data-2:[node] [a2798e72-83e2-4fad-a673-c5ff42ac9a0c]' has status: 'PENDING'."
		}],
		status: "WAITING"
	}],
	errors: [],
	status: "WAITING"
}
```

**Note:** The interrupt command can’t stop a block that is `STARTING`, but it will stop the change on the subsequent blocks.

Enter the `continue` command to resume the update process.

```
$ curl -X -H "Authorization: token=$(dcos config show core.dcos_acs_token)" POST http://<dcos_url>/service/hdfs/v1/plans/deploy/continue
```

After you execute the continue operation, the plan will look like this:

```
{
	phases: [{
		id: "77708b6f-52db-4361-a56f-4d2bd9d6bf09",
		name: "jn-deploy",
		steps: [{
			id: "fe96b235-bf26-4b36-ad0e-8dd3494b5b63",
			status: "COMPLETE",
			name: "journal-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'journal-0:[node] [fe96b235-bf26-4b36-ad0e-8dd3494b5b63]' has status: 'COMPLETE'."
		}, {
			id: "3a590b92-f2e8-439a-8951-4135b6c29b34",
			status: "COMPLETE",
			name: "journal-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'journal-1:[node] [3a590b92-f2e8-439a-8951-4135b6c29b34]' has status: 'COMPLETE'."
		}, {
			id: "c079bfcc-b620-4e0b-93d5-604223c0538d",
			status: "COMPLETE",
			name: "journal-2:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'journal-2:[node] [c079bfcc-b620-4e0b-93d5-604223c0538d]' has status: 'COMPLETE'."
		}],
		status: "COMPLETE"
	}, {
		id: "61a822e4-421d-4a46-b374-b0abaac6d2d4",
		name: "nn-deploy",
		steps: [{
			id: "0361e2f5-6e5d-42c8-9696-420f38ba5398",
			status: "COMPLETE",
			name: "name-0:[format]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-0:[format] [0361e2f5-6e5d-42c8-9696-420f38ba5398]' has status: 'COMPLETE'."
		}, {
			id: "a3a07cd4-828d-4161-b607-a745f3845abf",
			status: "COMPLETE",
			name: "name-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-0:[node] [a3a07cd4-828d-4161-b607-a745f3845abf]' has status: 'COMPLETE'."
		}, {
			id: "74ce8cf4-63cf-4ba2-ae6d-3ca6a389c66d",
			status: "COMPLETE",
			name: "name-1:[bootstrap]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-1:[bootstrap] [74ce8cf4-63cf-4ba2-ae6d-3ca6a389c66d]' has status: 'COMPLETE'."
		}, {
			id: "d46eb578-eb55-40b7-a683-7e44543ce63e",
			status: "COMPLETE",
			name: "name-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-1:[node] [d46eb578-eb55-40b7-a683-7e44543ce63e]' has status: 'COMPLETE'."
		}],
		status: "COMPLETE"
	}, {
		id: "f42c610b-e52b-4ce9-ab1c-887a123df234",
		name: "zkfc-deploy",
		steps: [{
			id: "9a5be25e-9997-4be9-bc67-1722056c8e8a",
			status: "COMPLETE",
			name: "zkfc-0:[format]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'zkfc-0:[format] [9a5be25e-9997-4be9-bc67-1722056c8e8a]' has status: 'COMPLETE'."
		}, {
			id: "2c8c9518-6d3c-48ed-bca1-58fd749da9c0",
			status: "COMPLETE",
			name: "zkfc-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'zkfc-0:[node] [2c8c9518-6d3c-48ed-bca1-58fd749da9c0]' has status: 'COMPLETE'."
		}, {
			id: "7e146767-21be-4d95-b83d-667a647b0503",
			status: "COMPLETE",
			name: "zkfc-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'zkfc-1:[node] [7e146767-21be-4d95-b83d-667a647b0503]' has status: 'COMPLETE'."
		}],
		status: "COMPLETE"
	}, {
		id: "774c5fec-7195-4ffd-850f-df62cf629fa9",
		name: "dn-deploy",
		steps: [{
			id: "f41b364f-5804-41ec-9d02-cd27f7b484ef",
			status: "COMPLETE",
			name: "data-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'data-0:[node] [f41b364f-5804-41ec-9d02-cd27f7b484ef]' has status: 'COMPLETE'."
		}, {
			id: "22d457dc-6ad4-4f6f-93f3-4c5071069503",
			status: "STARTING",
			name: "data-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'data-1:[node] [22d457dc-6ad4-4f6f-93f3-4c5071069503]' has status: 'STARTING'."
		}, {
			id: "a2798e72-83e2-4fad-a673-c5ff42ac9a0c",
			status: "STARTING",
			name: "data-2:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'data-2:[node] [a2798e72-83e2-4fad-a673-c5ff42ac9a0c]' has status: 'STARTING'."
		}],
		status: "STARTING"
	}],
	errors: [],
	status: "STARTING"
}
```

# Configuration Options

The following describes the most commonly used features of Beta-HDFS and how to configure them via the DC/OS CLI and the DC/OS GUI. There are two methods of configuring a HDFS cluster. The configuration may be specified using a JSON file during installation via the DC/OS command line (See the Installation section) or via modification to the Service Scheduler’s DC/OS environment at runtime (See the Configuration Update section). Note that some configuration options may only be specified at installation time.

## Service Configuration

The service configuration object contains properties that MUST be specified during installation and CANNOT be modified after installation is in progress. This configuration object is similar across all DC/OS Infinity services. Service configuration example:

```
{
    "service": {
        "name": "hdfs",
        "principal": "hdfs-principal",
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
    <td>principal</td>
    <td>string</td>
    <td>The authentication principal for the HDFS cluster.</td>
  </tr>

</table>

## Change the Service Name

- **In the DC/OS CLI, options.json**: `name` = string (default: `hdfs`)

## Node Configuration

The node configuration objects correspond to the configuration for nodes in the HDFS cluster. Node configuration MUST be specified during installation and MAY be modified during configuration updates. All of the properties except `disk` and `disk_type` MAY be modified during the configuration update process.

Example node configuration:
```
	"journal_node": {
		"cpus": 0.5,
		"mem": 4096,
		"disk": 10240,
		"disk_type": "ROOT",
		"strategy": "parallel"
	},
    "name_node": {
		"cpus": 0.5,
		"mem": 4096,
		"disk": 10240,
		"disk_type": "ROOT"
	},
    "zkfc_node": {
		"cpus": 0.5,
		"mem": 4096
	},
	"data_node": {
	    "count": 3,
		"cpus": 0.5,
		"mem": 4096,
		"disk": 10240,
		"disk_type": "ROOT",
		"strategy": "parallel"
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
    <td>strategy</td>
    <td>string</td>
    <td>The strategy used to deploy that node type. Possible values: <b>parallel</b> (default) and <b>serial</b>.
    <ul>
    <li><b>parallel:</b> All nodes of that type are deployed at the same time.</li>
    <li><b>serial:</b> All nodes of that type are deployed in sequence.</li>
    </ul>
    </td>
  </tr>

  <tr>
    <td>count</td>
    <td>integer</td>
    <td>The number of nodes of that node type for the cluster. There are always exactly two name nodes, so the name_node object has no count property. Users may select either 3 or 5 journal nodes. The default value of 3 is sufficient for most deployments and should only be overridden after careful thought. At least 3 data nodes should be configured, but this value may be increased to meet the storage needs of the deployment.</td>
  </tr>
</table>

## Add a Data Node
Increase the `DATA_COUNT` value from the DC/OS dashboard as described in the Configuring section. This creates an update plan as described in that section. An additional node will be added as the last step of that plan.

### Node Info

Comprehensive information is available about every node.  To list all nodes:

```bash
dcos beta-hdfs --name=<service-name> pods list
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
$ dcos beta-hdfs pods info journal-0
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

### Node Status
Similarly, the status for any node may also be queried.

```bash
$ dcos beta-hdfs --name=<service-name> pods info <node-id>
```

For example:

```bash
$ dcos beta-hdfs pods info journal-0
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
		"compress_image": true,
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
