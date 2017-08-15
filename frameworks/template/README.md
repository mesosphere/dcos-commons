This template README may be used as a starting point for writing a Service Guide for your DC/OS Service.

In particular, the parts in _ALL-CAPS ITALICS_ should be updated to reflect your service.

Many sections are left unfilled, as they depend on how your service works. For example, we leave empty sections for you to describe how users may [Backup and Restore their data](#disaster-recovery) because any persistent service should have a backup option.

# _SERVICENAME_ Service Guide

## Table of Contents

- [Overview](#overview)
  - [Features](#features)
- [Quick Start](#quick-start)
- [Installing and Customizing](#installing-and-customizing)
  - [Service Settings](#service-settings)
    - [Service Name](#service-name)
    - _SERVICE-WIDE OPTIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_
  - [Node Settings](#node-settings)
    - [Node Count](#node-count)
    - [CPU](#cpu)
    - [Memory](#memory)
    - [Ports](#ports)
    - [Storage Volumes](#storage-volumes)
    - [Placement Constraints](#placement-constraints)
    - _PER-NODE OPTIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_
- [Uninstalling](#uninstalling)
- [Connecting Clients](#connecting-clients)
  - [Discovering Endpoints](#discovering-endpoints)
  - [Connecting Clients to Endpoints](#connecting-clients-to-endpoints)
- [Managing](#managing)
  - [Updating Configuration](#updating-configuration)
    - [Adding a Node](#adding-a-node)
    - [Resizing a Node](#resizing-a-node)
    - [Updating Placement Constraints](#updating-placement-constraints)
    - _SERVICE-WIDE OPTIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_
    - _PER-NODE OPTIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_
  - [Restarting a Node](#restarting-a-node)
  - [Replacing a Node](#replacing-a-node)
  - [Upgrading Service Version](#upgrading)
  - _MAINTENANCE OPERATIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_
- [Disaster Recovery](#disaster-recovery)
  - _BACKUP OPTIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_
  - _RESTORE OPTIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_
- [Troubleshooting](#troubleshooting)
  - [Accessing Logs](#accessing-logs)
  - [Accessing Metrics](#accessing-metrics)
- [Limitations](#limitations)
  - [Removing a Node](#removing-a-node)
  - [Updating Storage Volumes](#updating-storage-volumes)
  - [Rack-aware Replication](#rack-aware-replication)
  - _CAVEATS SPECIFIC TO YOUR PRODUCT INTEGRATION_
- [Support](#support)
  - [Package Versioning Scheme](#package-versioning-scheme)
  - [Contacting Technical Support](#contacting-technical-support)
- [Changelog](#changelog)
  - [1.0.1-1.0.1](#1.0.1-1.0.1)
  - [1.0.0-1.0.0](#1.0.0-1.0.0)

<a name="overview"></a>
# Overview

DC/OS _SERVICENAME_ is an automated service that makes it easy to deploy and manage _SERVICENAME_ on [DC/OS](https://mesosphere.com/product/).

_BRIEF OVERVIEW OF YOUR PRODUCT_

<a name="features"></a>
## Features

- Single command installation for rapid provisioning
- CLI for easy management
- Multiple _SERVICENAME_ clusters sharing a single DC/OS cluster for multi-tenancy
- Multiple _SERVICENAME_ instances sharing the same hosts for improved utilization
- Placement constraints for fine-grained instance placement
- Vertical and horizontal for managing capacity
- Rolling software and configuration updates for runtime maintainence
- Integrated with Enterprise DC/OS Storage capabilities
- Integrated with Enterprise DC/OS Networking capabilities
- Integrated with Enterprise DC/OS Monitoring and Troubleshooting capabilities
- Integrated with Enterprise DC/OS Security capabilities
- _OTHER BENEFITS YOUR PRODUCT WITH DC/OS_


<a name="quick-start"></a>
# Quick Start

1. Install DC/OS on your cluster. See [the documentation](https://docs.mesosphere.com/latest/administration/installing/) for instructions.

1. If you are using open source DC/OS, install _SERVICENAME_ cluster with the following command from the DC/OS CLI. If you are using Enterprise DC/OS, you may need to follow additional instructions. See the Install and Customize section for more information.

	```
	dcos package install _PKGNAME_
	```

	You can also install _SERVICENAME_ from [the DC/OS web interface](https://docs.mesosphere.com/latest/usage/webinterface/).

1. The service will now deploy with a default configuration. You can monitor its deployment from the Services tab of the DC/OS web interface.

1. Connect a client to _SERVICENAME_.
	```
	dcos _PKGNAME_ endpoints
	[
	  "_LIST_",
	  "_OF_",
	  "_ENDPOINTS_"
	]
	dcos _PKGNAME_ endpoints _ENDPOINT_
	{
	  "address": ["10.0.3.156:_PORT_", "10.0.3.84:_PORT_"],
	  "dns": ["_POD_-0._PKGNAME_.mesos:_PORT_", "_POD_-1._PKGNAME_.mesos:_PORT_", "_POD_-2._PKGNAME_.mesos:_PORT_]
	}
	```

  1. _SIMPLE EXAMPLE OF HOW TO CONNECT A CLIENT AND INTERACT WITH YOUR PRODUCT (E.G., WRITE DATE, READ DATA)._

<a name="installing-and-customizing"></a>
# Installing and Customizing

The default _SERVICENAME_ installation provides reasonable defaults for trying out the service, but may not be sufficient for production use. You may require different configurations depending on the context of the deployment.

## Prerequisites
- If you are using Enterprise DC/OS, you may [need to provision a service account](https://docs.mesosphere.com/1.9/security/service-auth/custom-service-auth/) before installing _SERVICENAME_. Only someone with `superuser` permission can create the service account.
	- `strict` [security mode](https://docs.mesosphere.com/1.9/administration/installing/custom/configuration-parameters/#security) requires a service account.
	- `permissive` security mode a service account is optional.
	- `disabled` security mode does not require a service account.
- Your cluster must have at least _NUMBER_ private nodes.

## Installation from the DC/OS CLI

To start a basic test cluster, run the following command on the DC/OS CLI. Enterprise DC/OS users must follow additional instructions. [More information about installing _SERVICENAME_ on Enterprise DC/OS](https://docs.mesosphere.com/1.9/security/service-auth/custom-service-auth/).

```shell
dcos package install _PKGNAME_
```

You can specify a custom configuration in an `options.json` file and pass it to `dcos package install` using the `--options` parameter.

```shell
$ dcos package install _PKGNAME_ --options=your-options.json
```

**Recommendation:** Store your custom configuration in source control.

For more information about building the options.json file, see the [DC/OS documentation](https://docs.mesosphere.com/1.9/deploying-services/config-universe-service/) for service configuration access.

## Installation from the DC/OS Web Interface

 You can [install _SERVICENAME_ from the DC/OS web interface](https://docs.mesosphere.com/1.9/usage/managing-services/install/). If you install _SERVICENAME_ from the web interface, you must install the _SERVICENAME_ DC/OS CLI subcommands separately. From the DC/OS CLI, enter:

 ```bash
 dcos package install _SERVICENAME_ --cli
 ```

 Choose `ADVANCED INSTALLATION` to perform a custom installation.

<a name="service-settings"></a>
## Service Settings

<a name="service-name"></a>
### Service Name

Each instance of _SERVICENAME_ in a given DC/OS cluster must be configured with a different service name. You can configure the service name in the **service** section of the advanced installation section of the DC/OS web interface. The default service name (used in many examples here) is _`PKGNAME`_.

### _SERVICE-WIDE OPTIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_

_CREATE ONE OR MORE SECTIONS FOR ADDITIONAL SERVICE-WIDE CUSTOMIZATIONS THAT YOU EXPOSE._

_E.G., THIS MAY INCLUDE OPTIONAL FEATURES THAT MAY BE ENABLED/DISABLED BY A USER._

<a name="node-settings"></a>
## Node Settings

Adjust the following settings to customize the amount of resources allocated to each  node. _SERVICENAME_'s _[SYSTEM REQUIREMENTS](http://example.com)_ must be taken into consideration when adjusting these values. Reducing these values below those requirements may result in adverse performance and/or failures while using the service.

Each of the following settings can be customized under the **node** configuration section.

<a name="node-count"></a>
### Node Count

Customize the `Node Count` setting (default _DEFAULT NODE COUNT_) under the **node** configuration section. Consult _SERVICENAME_ documentation for minimum node count requirements.

<a name="cpu"></a>
### CPU

You can customize the amount of CPU allocated to each node. A value of `1.0` equates to one full CPU core on a machine. Change this value by editing the **cpus** value under the **node** configuration section. Turning this too low will result in throttled tasks.

<a name="memory"></a>
### Memory

You can customize the amount of RAM allocated to each node. Change this value by editing the **mem** value (in MB) under the **node** configuration section. Turning this too low will result in out of memory errors.

_ANY CUSTOMIZATIONS RELATING TO MEMORY THAT SHOULD BE ADJUSTED AS WELL (E.G. HEAP SIZE)? IF SO, MENTION THEM HERE._

<a name="ports"></a>
### Ports

You can customize the ports exposed by the service via the service configuratiton. If you wish to install multiple instances of the service and have them colocate on the same machines, you must ensure that **no** ports are common between those instances. Customizing ports is only needed if you require multiple instances sharing a single machine. This customization is optional otherwise. If your pod is on a virtual network (see below) then the port resources will be ignored allowing a pod to share a machine with another one using the same ports.

Each component's ports may be customized in the following configuration sections:
- _LIST PORT OPTIONS AND WHERE THEY ARE LOCATED IN THE CONFIG HERE_

<a name="storage-volumes"></a>

### Networks
You can specify that a pod should join a [Virtual Network](https://docs.mesosphere.com/latest/networking/virtual-networks/#virtual-network-service-dns) (such as the `dcos` overlay network) that supports having one IP address per pod.  When a pod joins a virtual network it gets its own IP address and has access to its own array of ports. Therefore when a pod specifies that it is joining dcos we ignore the ports resource requirements, because the pod will not consume the ports on the host machine. The DNS for pods on a virtual network network is <task_name>.<framework_name>.autoip.dcos.thisdcos.directory.

**Note** that this DNS will also work for pods on the host network. **Because the ports resources are not used when a pod is on the virtual network, we do not allow a pod to be moved from a virtual netowork to the host network or vice-versa.** This is to prevent potential starvation of the task when the host with the reserved resources for the task does not have the available ports required to launch the task.


### Storage Volumes

The service supports two volume types:
- `ROOT` volumes are effectively an isolated directory on the root volume, sharing IO/spindles with the rest of the host system.
- `MOUNT` volumes are a dedicated device or partition on a separate volume, with dedicated IO/spindles.

Using `MOUNT` volumes requires [additional configuration on each DC/OS agent system](https://docs.mesosphere.com/1.9/storage/mount-disk-resources/), so the service currently uses `ROOT` volumes by default. To ensure reliable and consistent performance in a production environment, you should configure `MOUNT` volumes on the machines that will run the service in your cluster and then configure the following as `MOUNT` volumes:

- _LIST ANY VOLUMES THAT SHOULD USE DEDICATED SPINDLES IN A PRODUCTION ENVIRONMENT FOR YOUR SERVICE_

<a name="placement-constraints"></a>
### Placement Constraints

Placement constraints allow you to customize where the service is deployed in the DC/OS cluster. Placement constraints may be configured _SEPARATELY FOR EACH NODE TYPE? (IF YOUR SERVICE HAS MULTIPLE TYPES)_ in the following configuration sections:
- _LIST EXPOSED PLACEMENT CONSTRAINT FIELDS AND WHERE THEY ARE LOCATED IN THE CONFIG HERE_

Placement constraints support all [Marathon operators](http://mesosphere.github.io/marathon/docs/constraints.html) with this syntax: `field:OPERATOR[:parameter]`. For example, if the reference lists `[["hostname", "UNIQUE"]]`, use `hostname:UNIQUE`.

A common task is to specify a list of whitelisted systems to deploy to. To achieve this, use the following syntax for the placement constraint:

```
hostname:LIKE:10.0.0.159|10.0.1.202|10.0.3.3
```

You must include spare capacity in this list, so that if one of the whitelisted systems goes down, there is still enough room to repair your service without that system.

For an example of updating placement constraints, see [Managing](#managing) below.

### _PER-NODE OPTIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_

_CREATE ONE OR MORE SECTIONS FOR ADDITIONAL PER-NODE CUSTOMIZATIONS THAT YOU EXPOSE. E.G., CUSTOMIZATION OF EXPOSED CONFIG FILE OPTIONS._

## _STEP-BY-STEP GUIDES FOR ANY ADDITIONAL CONFIG SCENARIOS TO POINT OUT_

_E.G., IF YOUR SERVICE SUPPORTS ENABLING/DISABLING CERTAIN COMPONENTS, THIS MAY BE A GOOD PLACE TO PROVIDE TUTORIALS ON HOW TO CONFIGURE THEM SUCCESSFULLY_

<a name="uninstalling"></a>
# Uninstalling

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

### DC/OS 1.10

If you are using DC/OS 1.10 and the installed service has a version greater than 2.0.0-x:

1. Uninstall the service. From the DC/OS CLI, enter `dcos package uninstall --app-id=<instancename> _PKGNAME_`.

For example, to uninstall a _SERVICENAME_ instance named `_PKGNAME_-dev`, run:

```bash
dcos package uninstall --app-id=_PKGNAME_-dev _PKGNAME_
```

### Older versions

If you are running DC/OS 1.9 or older, or a version of the service that is older than 2.0.0-x, follow these steps:

1. Stop the service. From the DC/OS CLI, enter `dcos package uninstall --app-id=<instancename> _PKGNAME_`.
   For example, `dcos package uninstall --app-id=_PKGNAME_-dev _PKGNAME_`.
1. Clean up remaining reserved resources with the framework cleaner script, `janitor.py`. See [DC/OS documentation](https://docs.mesosphere.com/1.9/deploying-services/uninstall/#framework-cleaner) for more information about the framework cleaner script.

For example, to uninstall a Confluent Kafka instance named `_PKGNAME_-dev`, run:

```bash
$ MY_SERVICE_NAME=_PKGNAME_-dev
$ dcos package uninstall --app-id=$MY_SERVICE_NAME _PKGNAME_`.
$ dcos node ssh --master-proxy --leader "docker run mesosphere/janitor /janitor.py \
    -r $MY_SERVICE_NAME-role \
    -p $MY_SERVICE_NAME-principal \
    -z dcos-service-$MY_SERVICE_NAME"
```

<!-- END DUPLICATE BLOCK -->

<a name="connecting-clients"></a>
# Connecting Clients

One of the benefits of running containerized services is that they can be placed anywhere in the cluster. Because they can be deployed anywhere on the cluster, clients need a way to find the service. This is where service discovery comes in.

<a name="discovering-endpoints"></a>
## Discovering Endpoints

Once the service is running, you may view information about its endpoints via either of the following methods:
- CLI:
  - List endpoint types: `dcos _PKGNAME_ endpoints`
  - View endpoints for an endpoint type: `dcos _PKGNAME_ endpoints <endpoint>`
- Web:
  - List endpoint types: `<dcos-url>/service/_PKGNAME_/v1/endpoints`
  - View endpoints for an endpoint type: `<dcos-url>/service/_PKGNAME_/v1/endpoints/<endpoint>`

Returned endpoints will include the following:
- `.autoip.dcos.thisdcos.directory` hostnames for each instance that will follow them if they're moved within the DC/OS cluster.
- A HA-enabled VIP hostname for accessing any of the instances (optional).
- A direct IP address for accesssing the service if `.autoip.dcos.thisdcos.directory` hostnames are not resolvable.
- If your service is on a virtual network such as the `dcos` overlay network, then the IP will be from the subnet allocated to the host that the task is running on. It will not be the host IP. To resolve the host IP use Mesos DNS (`<task>.<service>.mesos`).

In general, the `.autoip.dcos.thisdcos.directory` endpoints will only work from within the same DC/OS cluster. From outside the cluster you can either use the direct IPs or set up a proxy service that acts as a frontend to your _SERVICENAME_ instance. For development and testing purposes, you can use [DC/OS Tunnel](https://docs.mesosphere.com/latest/administration/access-node/tunnel/) to access services from outside the cluster, but this option is not suitable for production use.

<a name="connecting-clients-to-endpoints"></a>
## Connecting Clients to Endpoints

_GIVEN A RELEVANT EXAMPLE CLIENT FOR YOUR SERVICE, PROVIDE INSTRUCTIONS FOR CONNECTING THAT CLIENT USING THE ENDPOINTS LISTED ABOVE. WE RECOMMEND USING THE .MESOS ENDPOINTS IN YOUR EXAMPLE AS THEY WILL FOLLOW TASKS IF THEY ARE MOVED WITHIN THE CLUSTER._

<a name="managing"></a>
# Managing

<a name="updating-configuration"></a>
## Updating Configuration
You can make changes to the service after it has been launched. Configuration management is handled by the scheduler process, which in turn handles deploying _SERVICENAME_ itself.

After making a change, the scheduler will be restarted and will automatically deploy any detected changes to the service, one node at a time. For example, a given change will first be applied to `_NODEPOD_-0`, then `_NODEPOD_-1`, and so on.

Nodes are configured with a "Readiness check" to ensure that the underlying service appears to be in a healthy state before continuing with applying a given change to the next node in the sequence. However, this basic check is not foolproof and reasonable care should be taken to ensure that a given configuration change will not negatively affect the behavior of the service.

Some changes, such as decreasing the number of nodes or changing volume requirements, are not supported after initial deployment. See [Limitations](#limitations).

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

The instructions below describe how to update the configuration for a running DC/OS service.

### Enterprise DC/OS 1.10

Enterprise DC/OS 1.10 introduces a convenient command line option that allows for easier updates to a service's configuration, as well as allowing users to inspect the status of an update, to pause and resume updates, and to restart or complete steps if necessary.

#### Prerequisites

+ Enterprise DC/OS 1.10 or newer.
+ Service with a version greater than 2.0.0-x.
+ [The DC/OS CLI](https://docs.mesosphere.com/latest/cli/install/) installed and available.
+ The service's subcommand available and installed on your local machine.
  + You can install just the subcommand CLI by running `dcos package install --cli _PKGNAME_`.
  + If you are running an older version of the subcommand CLI that doesn't have the `update` command, uninstall and reinstall your CLI.
    ```bash
    dcos package uninstall --cli _PKGNAME_
    dcos package install --cli _PKGNAME_
    ```

#### Preparing configuration

If you installed this service with Enterprise DC/OS 1.10, you can fetch the full configuration of a service (including any default values that were applied during installation). For example:

```bash
$ dcos _PKGNAME_ describe > options.json
```

Make any configuration changes to this `options.json` file.

If you installed this service with a prior version of DC/OS, this configuration will not have been persisted by the the DC/OS package manager. You can instead use the `options.json` file that was used when [installing the service](#initial-service-configuration).

<strong>Note:</strong> You need to specify all configuration values in the `options.json` file when performing a configuration update. Any unspecified values will be reverted to the default values specified by the DC/OS service. See the "Recreating `options.json`" section below for information on recovering these values.

##### Recreating `options.json` (optional)

If the `options.json` from when the service was last installed or updated is not available, you will need to manually recreate it using the following steps.

First, we'll fetch the default application's environment, current application's environment, and the actual template that maps config values to the environment:

1. Ensure you have [jq](https://stedolan.github.io/jq/) installed.
1. Set the service name that you're using, for example:
```bash
$ SERVICE_NAME=_PKGNAME_
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

#### Starting the update

Once you are ready to begin, initiate an update using the DC/OS CLI, passing in the updated `options.json` file:

```bash
$ dcos _PKGNAME_ update start --options=options.json
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

To see a full listing of available options, run `dcos package describe --config _PKGNAME_` in the CLI, or browse the _SERVICE NAME_ install dialog in the DC/OS web interface.

<a name="adding-a-node"></a>
### Adding a Node
The service deploys _DEFAULT NODE COUNT_ nodes by default. You can customize this value at initial deployment or after the cluster is already running. Shrinking the cluster is not supported.

Modify the `NODE_COUNT` environment variable to update the node count. If you decrease this value, the scheduler will prevent the configuration change until it is reverted back to its original value or larger.

<a name="resizing-a-node"></a>
### Resizing a Node
The CPU and Memory requirements of each node can be increased or decreased as follows:
- CPU (1.0 = 1 core): `NODE_CPUS`
- Memory (in MB): `NODE_MEM` _MENTION ANY OTHER ENVVARS THAT SHOULD BE ADJUSTED ALONG WITH THE MEMORY ENVVAR HERE?_

**Note:** Volume requirements (type and/or size) cannot be changed after initial deployment.

<a name="updating-placement-constraints"></a>
### Updating Placement Constraints

Placement constraints can be updated after initial deployment using the following procedure. See [Service Settings](#service-settings) above for more information on placement constraints.

Let's say we have the following deployment of our nodes

- Placement constraint of: `hostname:LIKE:10.0.10.3|10.0.10.8|10.0.10.26|10.0.10.28|10.0.10.84`
- Tasks:
```
10.0.10.3: _NODEPOD_-0
10.0.10.8: _NODEPOD_-1
10.0.10.26: _NODEPOD_-2
10.0.10.28: empty
10.0.10.84: empty
```

`10.0.10.8` is being decommissioned and we should move away from it. Steps:

1. Remove the decommissioned IP and add a new IP to the placement rule whitelist by editing `NODE_PLACEMENT`:

	```
	hostname:LIKE:10.0.10.3|10.0.10.26|10.0.10.28|10.0.10.84|10.0.10.123
	```
1. Redeploy `_NODEPOD_-1` from the decommissioned node to somewhere within the new whitelist: `dcos _PKGNAME_ pod replace _NODEPOD_-1`
1. Wait for `_NODEPOD_-1` to be up and healthy before continuing with any other replacement operations.

### _SERVICE-WIDE OPTIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_

_ADD ONE OR MORE SECTIONS HERE TO DESCRIBE RE-CONFIGURATION OF HIGHLIGHTED SERVICE-WIDE OPTIONS EXPOSED BY YOUR PRODUCT INTEGRATION_

### _PER-NODE OPTIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_

_ADD ONE OR MORE SECTIONS HERE TO DESCRIBE RE-CONFIGURATION OF HIGHLIGHTED NODE-SPECIFIC OPTIONS THAT YOUR SERVICE EXPOSES_

<a name="restarting-a-node"></a>
## Restarting a Node

This operation will restart a node while keeping it at its current location and with its current persistent volume data. This may be thought of as similar to restarting a system process, but it also deletes any data that is not on a persistent volume.

1. Run `dcos _PKGNAME_ pod restart _NODEPOD_-<NUM>`, e.g. `_NODEPOD_-2`.

<a name="replacing-a-node"></a>
## Replacing a Node

This operation will move a node to a new system and will discard the persistent volumes at the prior system to be rebuilt at the new system. Perform this operation if a given system is about to be offlined or has already been offlined.

**Note:** Nodes are not moved automatically. You must perform the following steps manually to move nodes to new systems. You canbuild your own automation to perform node replacement automatically according to your own preferences.

1. _ANY STEPS TO WIND DOWN A NODE BEFORE IT'S WIPED/DECOMMISSIONED GO HERE_
1. Run `dcos _PKGNAME_ pod replace _NODEPOD_-<NUM>` to halt the current instance (if still running) and launch a new instance elsewhere.

For example, let's say `_NODEPOD_-3`'s host system has died and `_NODEPOD_-3` needs to be moved.

1. _DETAILED INSTRUCTIONS FOR WINDING DOWN A NODE, IF NEEDED FOR YOUR SERVICE, GO HERE_
1. _"NOW THAT THE NODE HAS BEEN DECOMMISSIONED," (IF NEEDED BY YOUR SERVICE)_ start `_NODEPOD_-3` at a new location in the cluster.
	``` shell
	$ dcos _PKGNAME_ pod replace _NODEPOD_-3
	```

<a name="upgrading"></a>
## Upgrading Service Version

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

The instructions below show how to safely update one version of _SERVICENAME_ to the next.

##### Viewing available versions

The `update package-versions` command allows you to view the versions of a service that you can upgrade or downgrade to. These are specified by the service maintainer and depend on the semantics of the service (i.e. whether or not upgrades are reversal).

For example, run:
```bash
$ dcos _PKGNAME_ update package-versions
```

##### Upgrading or downgrading a service

1. Before updating the service itself, update its CLI subcommand to the new version:
```bash
$ dcos package uninstall --cli _PKGNAME_
$ dcos package install --cli _PKGNAME_ --package-version="1.1.6-5.0.7"
```
1. Once the CLI subcommand has been updated, call the update start command, passing in the version. For example, to update _SERVICENAME_ to version `1.1.6-5.0.7`:
```bash
$ dcos _PKGNAME_ update start --package-version="1.1.6-5.0.7"
```

If you are missing mandatory configuration parameters, the `update` command will return an error. To supply missing values, you can also provide an `options.json` file (see [Updating configuration](#updating-configuration)):
```bash
$ dcos _PKGNAME_ update start --options=options.json --package-version="1.1.6-5.0.7"
```

See [Advanced update actions](#advanced-update-actions) for commands you can use to inspect and manipulate an update after it has started.

<!-- END DUPLICATE BLOCK -->

## Advanced update actions

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

The following sections describe advanced commands that be used to interact with an update in progress.

### Monitoring the update

Once the Scheduler has been restarted, it will begin a new deployment plan as individual pods are restarted with the new configuration. Depending on the high availability characteristics of the service being updated, you may experience a service disruption.

You can query the status of the update as follows:

```bash
$ dcos _PKGNAME_ update status
```

If the Scheduler is still restarting, DC/OS will not be able to route to it and this command will return an error message. Wait a short while and try again. You can also go to the Services tab of the DC/OS GUI to check the status of the restart.

### Pause

To pause an ongoing update, issue a pause command:

```bash
$ dcos _PKGNAME_ update pause
```

You will receive an error message if the plan has already completed or has been paused. Once completed, the plan will enter the `WAITING` state.

### Resume

If a plan is in a `WAITING` state, as a result of being paused or reaching a breakpoint that requires manual operator verification, you can use the `resume` command to continue the plan:

```bash
$ dcos _PKGNAME_ update resume
```

You will receive an error message if you attempt to `resume` a plan that is already in progress or has already completed.

### Force Complete

In order to manually "complete" a step (such that the Scheduler stops attempting to launch a task), you can issue a `force-complete` command. This will instruct to Scheduler to mark a specific step within a phase as complete. You need to specify both the phase and the step, for example:

```bash
$ dcos _PKGNAME_ update force-complete service-phase service-0:[node]
```

### Force Restart

Similar to force complete, you can also force a restart. This can either be done for an entire plan, a phase, or just for a specific step.

To restart the entire plan:
```bash
$ dcos _PKGNAME_ update force-restart
```

Or for all steps in a single phase:
```bash
$ dcos _PKGNAME_ update force-restart service-phase
```

Or for a specific step within a specific phase:
```bash
$ dcos _PKGNAME_ update force-restart service-phase service-0:[node]
```

<!-- END DUPLICATE BLOCK -->

<a name="disaster-recovery"></a>
# Disaster Recovery

## _BACKUP OPTIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_

_INSTRUCTIONS FOR BACKING UP DATA FROM YOUR SERVICE._

## _ RESTORE OPTIONS SPECIFIC TO YOUR PRODUCT INTEGRATION_

_INSTRUCTIONS FOR RESTORING BACKED UP DATA TO YOUR SERVICE._

<a name="troubleshooting"></a>
# Troubleshooting

<a name="accessing-logs"></a>
## Accessing Logs

Logs for the scheduler and all service nodes can be viewed from the DC/OS web interface.

- Scheduler logs are useful for determining why a node isn't being launched (this is under the purview of the Scheduler).
- Node logs are useful for examining problems in the service itself.

In all cases, logs are generally piped to files named `stdout` and/or `stderr`.

To view logs for a given node, perform the following steps:
1. Visit <dcos-url> to access the DC/OS web interface.
1. Navigate to `Services` and click on the service to be examined (default _`PKGNAME`_).
1. In the list of tasks for the service, click on the task to be examined (scheduler is named after the service, nodes are each `_NODEPOD_-#-node`).
1. In the task details, click on the `Logs` tab to go into the log viewer. By default, you will see `stdout`, but `stderr` is also useful. Use the pull-down in the upper right to select the file to be examined.

You can also access the logs via the Mesos UI:
1. Visit <dcos-url>/mesos to view the Mesos UI.
1. Click the `Frameworks` tab in the upper left to get a list of services running in the cluster.
1. Navigate into the correct framework for your needs. The scheduler runs under `marathon` with a task name matching the service name (default _`PKGNAME`_). Service nodes run under a framework whose name matches the service name (default _`PKGNAME`_).
1. You should now see two lists of tasks. `Active Tasks` are tasks currently running, and `Completed Tasks` are tasks that have exited. Click the `Sandbox` link for the task you wish to examine.
1. The `Sandbox` view will list files named `stdout` and `stderr`. Click the file names to view the files in the browser, or click `Download` to download them to your system for local examination. Note that very old tasks will have their Sandbox automatically deleted to limit disk space usage.

<a name="accessing-metrics"></a>
_INSTRUCTIONS FOR ACCESSING METRICS._

<a name="limitations"></a>
# Limitations

_MANAGE CUSTOMER EXPECTIONS BY DISCLOSING ANY FEATURES OF YOUR PRODUCT THAT ARE NOT SUPPORTED WITH DC/OS, FEATURES MISSING FROM THE DC/OS INTEGRATION, ETC._

<a name="removing-a-node"></a>
## Removing a Node

Removing a node is not supported at this time.

<a name="updating-storage-volumes"></a>
## Updating Storage Volumes

Neither volume type nor volume size requirements may be changed after initial deployment.

<a name="rack-aware-replication"></a>
## Rack-aware Replication

Rack placement and awareness are not supported at this time.

## Virtual network configuration updates
When a pod from your service uses a virtual network, it does not use the port resources on the agent machine, and thus does not have them reserved. For this reason, we do not allow a pod deployed on a virtual network to be updated (moved) to the host network, because we cannot guarantee that the machine with the reserved volumes will have ports available. To make the reasoning simpler, we also do not allow for pods to be moved from the host network to a virtual network. Once you pick a networking paradigm for your service the service is bound to that networking paradigm.

## _OTHER CAVEATS SPECIFIC TO YOUR PRODUCT INTEGRATION_

<a name="support"></a>
# Support

<a name="package-versioning-scheme"></a>
## Package Versioning Scheme

- _SERVICENAME_: _WHAT VERSION OF YOUR SERVICE IS INCLUDED IN THE PACKAGE?_
- DC/OS: _LIST VERSION(S) OF DC/OS THAT YOU'VE TESTED AND SUPPORT_

Packages are versioned with an `a.b.c-x.y.z` format, where `a.b.c` is the version of the DC/OS integration and `x.y.z` indicates the version of _SERVICENAME_. For example, `1.5.0-3.2.1` indicates version `1.5.0` of the DC/OS integrtion and version `3.2.1` of _SERVICENAME_.

<a name="contacting-technical-support"></a>
## Contacting Technical Support

### _YOUR TECHNICAL SUPPORT CONTACT INFORMATION_

### Mesosphere
[Submit a request](https://support.mesosphere.com/hc/en-us/requests/new).

<a name="changelog"></a>
## Changelog

### 1.0.1-1.0.0
#### Breaking Changes
#### New Features
#### Improvements
#### Bug Fixes

### 1.0.0-1.0.0
#### Breaking Changes
#### Features
#### Improvements
#### Bug Fixes
