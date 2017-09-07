---
post_title: Configuring
menu_order: 30
enterprise: 'no'
---

# Changing Configuration at Runtime

You can customize your cluster in-place when it is up and running.

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

The instructions below describe how to update the configuration for a running DC/OS service.

## Enterprise DC/OS 1.10

Enterprise DC/OS 1.10 introduces a convenient command line option that allows for easier updates to a service's configuration, as well as allowing users to inspect the status of an update, to pause and resume updates, and to restart or complete steps if necessary.

### Prerequisites

+ Enterprise DC/OS 1.10 or newer.
+ Service with a version greater than 2.0.0-x.
+ [The DC/OS CLI](https://docs.mesosphere.com/latest/cli/install/) installed and available.
+ The service's subcommand available and installed on your local machine.
  + You can install just the subcommand CLI by running `dcos package install --cli beta-hdfs`.
  + If you are running an older version of the subcommand CLI that doesn't have the `update` command, uninstall and reinstall your CLI.
    ```bash
    dcos package uninstall --cli beta-hdfs
    dcos package install --cli beta-hdfs
    ```

### Preparing configuration

If you installed this service with Enterprise DC/OS 1.10, you can fetch the full configuration of a service (including any default values that were applied during installation). For example:

```bash
$ dcos beta-hdfs describe > options.json
```

Make any configuration changes to this `options.json` file.

If you installed this service with a prior version of DC/OS, this configuration will not have been persisted by the the DC/OS package manager. You can instead use the `options.json` file that was used when [installing the service](#initial-service-configuration).

**Note:** You must specify all configuration values in the `options.json` file when performing a configuration update. Any unspecified values will be reverted to the default values specified by the DC/OS service. See the "Recreating `options.json`" section below for information on recovering these values.

#### Recreating `options.json` (optional)

If the `options.json` from when the service was last installed or updated is not available, you will need to manually recreate it using the following steps.

First, we'll fetch the default application's environment, current application's environment, and the actual template that maps config values to the environment:

1. Ensure you have [jq](https://stedolan.github.io/jq/) installed.
1. Set the service name that you're using, for example:
```bash
$ SERVICE_NAME=beta-hdfs
```
1. Get the version of the package that is currently installed:
```bash
$ PACKAGE_VERSION=$(dcos package list | grep $SERVICE_NAME | awk '{print $2}')
```
1. Then fetch and save the environment variables that have been set for the service:
```bash
$ dcos marathon app show $SERVICE_NAME | jq .env > current_env.json
```
1. To identify those values that are custom, we'll get the default environment variables for this version of the service:
```bash
$ dcos package describe --package-version=$PACKAGE_VERSION --render --app $SERVICE_NAME | jq .env > default_env.json
```
1. We'll also get the entire application template:
```bash
$ dcos package describe $SERVICE_NAME --app > marathon.json.mustache
```

Now that you have these files, we'll attempt to recreate the `options.json`.

1. Use JQ and `diff` to compare the two:
```bash
$ diff <(jq -S . default_env.json) <(jq -S . current_env.json)
```
1. Now compare these values to the values contained in the `env` section in application template:
```bash
$ less marathon.json.mustache
```
1. Use the variable names (e.g. `{{service.name}}`) to create a new `options.json` file as described in [Initial service configuration](#initial-service-configuration).

### Starting the update

Once you are ready to begin, initiate an update using the DC/OS CLI, passing in the updated `options.json` file:

```bash
$ dcos beta-hdfs update start --options=options.json
```

You will receive an acknowledgement message and the DC/OS package manager will restart the Scheduler in Marathon.

See [Advanced update actions](#advanced-update-actions) for commands you can use to inspect and manipulate an update after it has started.

### Open Source DC/OS, Enterprise DC/OS 1.9 and Earlier

If you do not have Enterprise DC/OS 1.10 or later, the CLI commands above are not available. For Open Source DC/OS of any version, or Enterprise DC/OS 1.9 and earlier, you can perform changes from the DC/OS GUI.

<!-- END DUPLICATE BLOCK -->
These are the general steps to follow:

1.  Go to the **Services** tab of the DC/OS GUI and click the name of the HDFS service to be updated.

	![HFDS in DC/OS GUI](/img/hdfs-service-gui.png)

1.  Within the HDFS instance details view, click the vertical ellipsis menu in the upper right, then choose **Edit**.

	![Edit tab](/img/hdfs-service-gui2.png)

1.  Click the **Environment** tab and make your updates. For example, to increase the number of nodes, edit the value for `DATA_COUNT`.

	![Edit environment](/img/hdfs-service-gui3.png)

1. Click **REVIEW & RUN** to apply any changes and cleanly reload the HDFS scheduler. The HDFS cluster itself will persist across the change.

## Configuration Deployment Strategy

Configuration updates are rolled out through execution of update plans. You can configure the way these plans are executed.

### Configuration Update Plans

This configuration update strategy is analogous to the installation procedure above. If the configuration update is accepted, there will be no errors in the generated plan, and a rolling restart will be performed on all nodes to apply the updated configuration. However, the default strategy can be overridden by a strategy the user provides.

# Configuration Update

Make the REST request below to view the current plan. See the REST API Authentication part of the REST API Reference section for information on how this request must be authenticated.

```bash
$ curl -v -H "Authorization: token=$(dcos config show core.dcos_acs_token)" "http://<dcos_url>/service/hdfs/v1/plans/deploy"
```

The response will look similar to this:

```json
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

```bash
$ curl -X -H "Authorization: token=$(dcos config show core.dcos_acs_token)" POST http:/<dcos_url>/service/hdfs/v1/plans/deploy/interrupt
```


If you query the plan again, the response will look like this (notice `status: "Waiting"`):

```json
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

```bash
$ curl -X -H "Authorization: token=$(dcos config show core.dcos_acs_token)" POST http://<dcos_url>/service/hdfs/v1/plans/deploy/continue
```

After you execute the continue operation, the plan will look like this:

```json
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

The following describes the most commonly used features of DC/OS Apache HDFS and how to configure them via the DC/OS CLI and the DC/OS GUI. There are two methods of configuring an HDFS cluster. The configuration may be specified using a JSON file during installation via the DC/OS command line (See the Installation section) or via modification to the Service Scheduler’s DC/OS environment at runtime (See the Configuration Update section). Note that some configuration options may only be specified at installation time.

## Service Configuration

The service configuration object contains properties that MUST be specified during installation and CANNOT be modified after installation is in progress. This configuration object is similar across all DC/OS Infinity services. Service configuration example:

```json
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
```json
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


## HDFS File System Configuration

The HDFS file system network configuration, permissions, and compression is configured via the `hdfs` JSON object. Once these properties are set at installation time they can not be reconfigured.
Example HDFS configuration:

```json
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
