---
post_title: Managing
menu_order: 60
enterprise: 'no'
---

# Updating Configuration
You can make changes to the service after it has been launched. Configuration management is handled by the scheduler process, which in turn handles deploying DC/OS HDFS Service itself.

After making a change, the scheduler will be restarted and will automatically deploy any detected changes to the service, one node at a time. For example, a given change will first be applied to `_NODEPOD_-0`, then `_NODEPOD_-1`, and so on.

Nodes are configured with a "Readiness check" to ensure that the underlying service appears to be in a healthy state before continuing with applying a given change to the next node in the sequence. However, this basic check is not foolproof and reasonable care should be taken to ensure that a given configuration change will not negatively affect the behavior of the service.

Some changes, such as decreasing the number of nodes or changing volume requirements, are not supported after initial deployment. See [Limitations](#limitations).

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
    $ dcos package uninstall --cli beta-hdfs
    $ dcos package install --cli beta-hdfs
    ```

### Preparing configuration

If you installed this service with Enterprise DC/OS 1.10, you can fetch the full configuration of a service (including any default values that were applied during installation). For example:

```bash
$ dcos beta-hdfs describe > options.json
```

Make any configuration changes to this `options.json` file.

If you installed this service with a prior version of DC/OS, this configuration will not have been persisted by the the DC/OS package manager. You can instead use the `options.json` file that was used when [installing the service](#initial-service-configuration).

<strong>Note:</strong> You need to specify all configuration values in the `options.json` file when performing a configuration update. Any unspecified values will be reverted to the default values specified by the DC/OS service. See the "Recreating `options.json`" section below for information on recovering these values.

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

To make configuration changes via scheduler environment updates, perform the following steps:
1. Visit <dcos-url> to access the DC/OS web interface.
1. Navigate to `Services` and click on the service to be configured (default _`PKGNAME`_).
1. Click `Edit` in the upper right. On DC/OS 1.9.x, the `Edit` button is in a menu made up of three dots.
1. Navigate to `Environment` (or `Environment variables`) and search for the option to be updated.
1. Update the option value and click `Review and run` (or `Deploy changes`).
1. The Scheduler process will be restarted with the new configuration and will validate any detected changes.
1. If the detected changes pass validation, the relaunched Scheduler will deploy the changes by sequentially relaunching affected tasks as described above.

To see a full listing of available options, run `dcos package describe --config beta-hdfs` in the CLI, or browse the _SERVICE NAME_ install dialog in the DC/OS web interface.

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

The following describes the most commonly used features of HDFS and how to configure them via the DC/OS CLI and the DC/OS GUI. There are two methods of configuring an HDFS cluster. The configuration may be specified using a JSON file during installation via the DC/OS command line (See the Installation section) or via modification to the Service Scheduler’s DC/OS environment at runtime (See the Configuration Update section). Note that some configuration options may only be specified at installation time.

## Service Configuration

The service configuration object contains properties that MUST be specified during installation and CANNOT be modified after installation is in progress. This configuration object is similar across all DC/OS Infinity services. Service configuration example:

```json
{
    "service": {
        "name": "hdfs",
        "service_account": "hdfs-principal",
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
    <td>service_account</td>
    <td>string</td>
    <td>The service account for the HDFS cluster.</td>
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

### Node Info

Comprehensive information is available about every node.  To list all nodes:

```bash
dcos beta-hdfs --name=<service-name> pod list
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
$ dcos hdfs --name=<service-name> pod info <node-id>
```

For example:
```bash
$ dcos beta-hdfs pod info journal-0
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
              "value": "dn_socket"
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
$ dcos beta-hdfs --name=<service-name> pod status <node-id>
```

For example:

```bash
$ dcos beta-hdfs pod status journal-0
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

# Upgrading Service Version

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

The instructions below show how to safely update one version of DC/OS HDFS Service to the next.

## Viewing available versions

The `update package-versions` command allows you to view the versions of a service that you can upgrade or downgrade to. These are specified by the service maintainer and depend on the semantics of the service (i.e. whether or not upgrades are reversal).

For example, run:
```bash
$ dcos beta-hdfs update package-versions
```

## Upgrading or downgrading a service

1. Before updating the service itself, update its CLI subcommand to the new version:
```bash
$ dcos package uninstall --cli beta-hdfs
$ dcos package install --cli beta-hdfs --package-version="1.1.6-5.0.7"
```
1. Once the CLI subcommand has been updated, call the update start command, passing in the version. For example, to update DC/OS HDFS Service to version `1.1.6-5.0.7`:
```bash
$ dcos beta-hdfs update start --package-version="1.1.6-5.0.7"
```

If you are missing mandatory configuration parameters, the `update` command will return an error. To supply missing values, you can also provide an `options.json` file (see [Updating configuration](#updating-configuration)):
```bash
$ dcos beta-hdfs update start --options=options.json --package-version="1.1.6-5.0.7"
```

See [Advanced update actions](#advanced-update-actions) for commands you can use to inspect and manipulate an update after it has started.

<!-- END DUPLICATE BLOCK -->

# Advanced update actions

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

The following sections describe advanced commands that be used to interact with an update in progress.

## Monitoring the update

Once the Scheduler has been restarted, it will begin a new deployment plan as individual pods are restarted with the new configuration. Depending on the high availability characteristics of the service being updated, you may experience a service disruption.

You can query the status of the update as follows:

```bash
$ dcos beta-hdfs update status
```

If the Scheduler is still restarting, DC/OS will not be able to route to it and this command will return an error message. Wait a short while and try again. You can also go to the Services tab of the DC/OS GUI to check the status of the restart.

## Pause

To pause an ongoing update, issue a pause command:

```bash
$ dcos beta-hdfs update pause
```

You will receive an error message if the plan has already completed or has been paused. Once completed, the plan will enter the `WAITING` state.

## Resume

If a plan is in a `WAITING` state, as a result of being paused or reaching a breakpoint that requires manual operator verification, you can use the `resume` command to continue the plan:

```bash
$ dcos beta-hdfs update resume
```

You will receive an error message if you attempt to `resume` a plan that is already in progress or has already completed.

## Force Complete

In order to manually "complete" a step (such that the Scheduler stops attempting to launch a task), you can issue a `force-complete` command. This will instruct to Scheduler to mark a specific step within a phase as complete. You need to specify both the phase and the step, for example:

```bash
$ dcos beta-hdfs update force-complete service-phase service-0:[node]
```

## Force Restart

Similar to force complete, you can also force a restart. This can either be done for an entire plan, a phase, or just for a specific step.

To restart the entire plan:
```bash
$ dcos beta-hdfs update force-restart
```

Or for all steps in a single phase:
```bash
$ dcos beta-hdfs update force-restart service-phase
```

Or for a specific step within a specific phase:
```bash
$ dcos beta-hdfs update force-restart service-phase service-0:[node]
```

<!-- END DUPLICATE BLOCK -->

# Replacing Journal Nodes
The following section describes how to perform a `replace` of a Journal Node. This guide uses Journal Node 0 to
refer to the unhealthy Journal Node as it's the replaced Journal Node.

## Replace Command

Replace the Journal Node via:
```bash
$ dcos beta-hdfs pod replace journal-0
```

## Detecting an unhealthy Journal Node after `replace`

Once the replaced Journal Node is up and running, you should see the following in the `stderr` log:
```
org.apache.hadoop.hdfs.qjournal.protocol.JournalNotFormattedException: Journal Storage Directory
```

This indicates this Journal Node is unhealthy.

## Determining a healthy Journal Node

From the non-replaced Journal Nodes, confirm that a Journal Node is healthy:
  - Inspect the `stderr` log and check for absence of errors.
  - In `journal-data/hdfs/current`, check for:
    - consecutive `edits_xxxxx-edits_xxxxx` files with timestamps between each differing by ~2 minutes.
    - An `edits_inprogess_` file modified within the past 2 minutes.

Once identified, make a note of which Journal Node is healthy.

## Fixing the unhealthy Journal Node

1. SSH into the sandbox of the unhealthy Journal Node via
```bash
$ dcos task exec -it journal-0 /bin/bash
```

2. In this sandbox, create the directory `journal-data/hdfs/current`:
```bash
$ mkdir -p journal-data/hdfs/current
```

3. From the healthy Journal Node identified previously, copy the contents of the `VERSION` file into `journal-data/hdfs/current/VERSION`.

4. On the unhealthy Journal Node, create a file with the same path as the `VERSION` file on the healthy Journal Node:
`journal-data/hdfs/current/VERSION`. Paste the copied contents into this file.

5. Restart the unhealthy Journal Node via:
```bash
$ dcos beta-hdfs pod restart journal-0
```

6. Once the restarted Journal Node is up and running, confirm that it is now healthy again by inspecting the `stderr` log. You should see:
```bash
INFO namenode.FileJournalManager: Finalizing edits file
```
