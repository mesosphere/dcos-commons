<p align="left"><img src="https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/dcos-sdk-logo.png" width="250"/></p>

# Your Name Here

This template README may be used as a starting point for writing a Service Guide for your DC/OS Service.

In particular, the parts in _ALL-CAPS ITALICS_ should be updated to reflect your service.

Many sections are left unfilled as they depend on how your service works. For example, we leave empty sections for you to describe how users may [Backup and Restore their data](#disaster-recovery), because any persistent service should have a backup option.

---

# _SERVICENAME_ Service Guide

## Table of Contents

- [Overview](#overview)
  - Features
- [Quick Start](#quick-start)
- [Installing and Customizing](#installing-and-customizing)
  - Installation with Default Settings
  - Installation with Custom Settings
  - Service Settings
    - Service Name
    - _SERVICE-WIDE OPTIONS SPECIFIC TO YOUR IMPLEMENTATION GO HERE_
  - Node Settings
    - Node Count
    - CPU
    - Memory
    - Ports
    - Storage Volumes
    - Placement Constraints
    - _PER-NODE OPTIONS SPECIFIC TO YOUR IMPLEMENTATION GO HERE_
  - _STEP-BY-STEP GUIDES FOR ANY ADDITIONAL CONFIG SCENARIOS TO POINT OUT_
- [Uninstalling](#uninstalling)
- [Connecting Clients](#connecting-clients)
  - Discovering endpoints
  - Connecting clients to endpoints
- [Managing](#managing)
  - Updating Configuration
    - Adding a Node
    - Resizing a Node
    - _PER-NODE OPTIONS SPECIFIC TO YOUR IMPLEMENTATION GO HERE_
    - Updating Placement Constraints
    - _SERVICE-WIDE OPTIONS SPECIFIC TO YOUR IMPLEMENTATION GO HERE_
  - Restarting nodes
  - Replacing nodes
- [Disaster Recovery](#disaster-recovery)
  - Backup
  - Restore
- [Deployment Best Practices](#deploy-best-practices)
- [Troubleshooting](#troubleshooting)
  - Accessing Logs
- [Known Issues](#knownissues)
- [Limitations](#limitations)
  - Removing a Node
  - Automatic Failed Node Recovery
  - Updating Storage Volumes
  - Rack-aware Replication
  - _ANY OTHER CAVEATS TO MENTION HERE?_
- [Terms](#terms)
- [Support](#support)
  - Supported Versions
  - Package Versioning
    - Upgrades/downgrades
  - Reaching Technical Support

<a name="overview"></a>
# Overview

DC/OS _SERVICENAME_ is an automated service that makes it easy to deploy and manage _SERVICENAME_ on [Mesosphere DC/OS](http://dcos.io). For more information on _SERVICENAME_, see its _[documentation](http://example.com)_.

The service comes with a reasonable initial configuration for evaluation use. Additional customizations may be made to the service configuration at initial install, and later updated once the service is already running through a configuration rollout process. If you just want to try out the service, you can use the default configuration and be up and running within moments.

Interoperating clients and services may take advantage of DC/OS service discovery features to directly access _SERVICENAME_ via advertised endpoints, regardless of where the instance is currently located within a DC/OS Cluster.

Multiple instances can be installed on DC/OS and managed independently. This allows different teams within an organization to have isolated instances of the service.

## Features

- _BENEFITS OF YOUR IMPLEMENTATION GO HERE. WHAT BENEFITS ARE PROVIDED OVER THE ALTERNATIVES?_
- Multiple instances sharing the same physical systems (requires custom port configuration).
- Vertical (resource) and horizontal (node count) scaling.
- Easy redeployment to new systems upon scheduled or unscheduled outages.
- Consistent DNS addresses regardless of where nodes are located in the cluster.
- Node placement may be customized via Placement Constraints.

<a name="quick-start"></a>
# Quick Start

1. Get a DC/OS cluster. If you don't have one yet, head over to [DC/OS Docs](https://dcos.io/docs/latest) for instructions.
2. Install the Service in your DC/OS cluster, either via the [DC/OS Dashboard](https://docs.mesosphere.com/latest/usage/webinterface/) or via the [DC/OS CLI](https://docs.mesosphere.com/latest/usage/cli/) as shown here:
```
dcos config set core.dcos_url http://your-cluster.com
dcos config set core.ssl_verify False # optional
dcos auth login
```
```
dcos package install _PKGNAME_
```
3. The service will now deploy with a default configuration. You can monitor its deployment via the Services UI in the DC/OS Dashboard.
4. Now you are ready to connect a client to the service...
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

<a name="installing-and-customizing"></a>
# Installing and Customizing

When installing the service without any additional customizations, reasonable defaults are provided, but different configurations are recommended depending on the context of the deployment. The defaults are reasonable for trying out the service but not necessarily for production use.

## Installation from CLI

From the DC/OS CLI, _SERVICENAME_ may be installed with a default testing/non-production configuration as follows:
```
dcos package install _PKGNAME_
```

A custom configuration may be specified in an `options.json` file and passed to the the DC/OS CLI as follows:
```
$ dcos package install _PKGNAME_ --options=your-options.json
```

For more information about building the options.json file, see the [DC/OS documentation](https://docs.mesosphere.com/latest/usage/managing-services/config-universe-service/) for service configuration access.

## Installation from Web

From the DC/OS Dashboard webpage, _SERVICENAME_ may be installed with a default configuration as follows:
1. Visit http://yourcluster.com/ to view the DC/OS Dashboard.
1. Navigate to `Universe` => `Packages` and find the `_PKGNAME_` package.
1. Click `Install`, then in the pop up dialog click `Install` again to use default settings.

A customized installation may be performed from the DC/OS Dashboard as follows:
1. Visit http://yourcluster.com/ to view the DC/OS Dashboard.
1. Navigate to `Universe` => `Packages` and find the `_PKGNAME_` package.
1. Click `Install`, then in the pop up dialog click `Advanced` to see the customization dialog.
1. Make your changes to the default configuration in the customization dialog, then click `Review`.
1. Examine the configuration summary for any needed changes. Click `Back` to make changes, or `Install` to confirm the settings and install the service.

## Service Settings

### Service Name

Each instance of _SERVICENAME_ in a given DC/OS cluster must be configured with a different service name. You can configure the service name in the **service** section of the install settings. The default service name (used in many examples here) is _`PKGNAME`_.

### _SERVICE-WIDE OPTIONS SPECIFIC TO YOUR IMPLEMENTATION GO HERE_

_CREATE ONE OR MORE SECTIONS FOR ADDITIONAL SERVICE-WIDE CUSTOMIZATIONS THAT YOU EXPOSE._

_FOR EXAMPLE, THIS MAY INCLUDE OPTIONAL FEATURES THAT MAY BE ENABLED/DISABLED BY A USER._

## Node Settings

The following settings may be adjusted to customize the amount of resources allocated to each node. _SERVICENAME_'s _[SYSTEM REQUIREMENTS](http://example.com)_ must be taken into consideration when adjusting these values. Reducing these values below those requirements may result in adverse performance and/or failures while using the service.

Each of the following settings may be customized under the **node** configuration section.

### Node Count

Customize the `Node Count` setting (default _DEFAULT NODE COUNT_) under the **node** configuration section. Consult _SERVICENAME_ documentation for minimum node count requirements.

### CPU

The amount of CPU allocated to each node may be customized. A value of `1.0` equates to one full CPU core on a machine. This value may be customized by editing the **cpus** value under the **node** configuration section. Turning this too low will result in throttled tasks.

### Memory

The amount of RAM allocated to each node may be customized. This value may be customized by editing the **mem** value (in MB) under the **node** configuration section. Turning this too low will result in out of memory errors.

_ANY CUSTOMIZATIONS RELATING TO MEMORY THAT SHOULD BE ADJUSTED AS WELL (E.G. HEAP SIZE)? IF SO, MENTION THEM HERE._

### Ports

Each port exposed by the service may be customized via the service configuratiton. If you wish to install multiple instances of the service and have them colocate on the same machines, you must ensure that **no** ports are common between those instances. Customizing ports is only needed if you require multiple instances sharing a single machine. This customization is optional otherwise.

Each component's ports may be customized in the following configuration sections:
- _LIST PORT OPTIONS AND WHERE THEY ARE LOCATED IN THE CONFIG HERE_

### Storage Volumes

The service supports two volume types:
- `ROOT` volumes are effectively an isolated directory on the root volume, sharing IO/spindles with the rest of the host system.
- `MOUNT` volumes are a dedicated device or partition on a separate volume, with dedicated IO/spindles.

Using `MOUNT` volumes requires [additional configuration on each DC/OS agent system](https://dcos.io/docs/1.8/administration/storage/mount-disk-resources/), so the service currently uses `ROOT` volumes by default. To ensure reliable and consistent performance in a production environment, you should configure `MOUNT` volumes on the machines which will run the service in your cluster and then configure the following as `MOUNT` volumes:
- _LIST ANY VOLUMES THAT SHOULD USE DEDICATED SPINDLES IN A PRODUCTION ENVIRONMENT FOR YOUR SERVICE_

### Placement Constraints

Placement constraints allow you to customize where the service is deployed in the DC/OS cluster. Placement constraints may be configured _SEPARATELY FOR EACH NODE TYPE? (IF YOUR SERVICE HAS MULTIPLE TYPES)_ in the following configuration sections:
- _LIST EXPOSED PLACEMENT CONSTRAINT FIELDS AND WHERE THEY ARE LOCATED IN THE CONFIG HERE_

Placement constraints support all [Marathon operators (reference)](http://mesosphere.github.io/marathon/docs/constraints.html) with this syntax: `field:OPERATOR[:parameter]`. For example, if the reference lists `[["hostname", "UNIQUE"]]`, you should  use `hostname:UNIQUE`.

A common task is to specify a list of whitelisted systems to deploy to. To achieve this, use the following syntax for the placement constraint:
```
hostname:LIKE:10.0.0.159|10.0.1.202|10.0.3.3
```

You must include spare capacity in this list so that if one of the whitelisted systems goes down, there is still enough room to repair your service without that system.

For an example of updating placement constraints, see [Managing](#managing) below.

### _PER-NODE OPTIONS SPECIFIC TO YOUR IMPLEMENTATION GO HERE_

_CREATE ONE OR MORE SECTIONS FOR ADDITIONAL PER-NODE CUSTOMIZATIONS THAT YOU EXPOSE. FOR EXAMPLE, CUSTOMIZATION OF EXPOSED CONFIG FILE OPTIONS._

## _STEP-BY-STEP GUIDES FOR ANY ADDITIONAL CONFIG SCENARIOS TO POINT OUT_

_FOR EXAMPLE, IF YOUR SERVICE SUPPORTS ENABLING/DISABLING CERTAIN COMPONENTS, THIS MAY BE A GOOD PLACE TO PROVIDE TUTORIALS ON HOW TO CONFIGURE THEM SUCCESSFULLY_

<a name="uninstalling"></a>
# Uninstalling

Follow these steps to uninstall the service.

1. Stop the service. From the DC/OS CLI, enter `dcos package uninstall`.
1. Clean up remaining reserved resources with the framework cleaner script, `janitor.py`. [More information about the framework cleaner script](https://docs.mesosphere.com/1.8/usage/managing-services/uninstall/#framework-cleaner).

To uninstall an instance named `_PKGNAME_` (the default), run:
```
MY_SERVICE_NAME=_PKGNAME_
dcos package uninstall --app-id=$MY_SERVICE_NAME _PKGNAME_
dcos node ssh --master-proxy --leader "docker run mesosphere/janitor /janitor.py \
    -r $MY_SERVICE_NAME-role \
    -p $MY_SERVICE_NAME-principal \
    -z dcos-service-$MY_SERVICE_NAME"
```

<a name="connecting-clients"></a>
# Connecting clients

One of the benefits of running containerized services is that they can be placed anywhere in the cluster. This benefit brings up the question on how to find those services once they're deployed. Clients need a way to connect to the service regardless of where it's currently located in the cluster. This is where service discovery comes in.

## Discovering endpoints

Once the service is running, you may view information about its endpoints via either of the following methods:
- CLI:
  - List endpoint types: `dcos _PKGNAME_ endpoints`
  - View endpoints for an endpoint type: `dcos _PKGNAME_ endpoints <endpoint>`
- Web:
  - List endpoint types: `https://yourcluster.com/service/_PKGNAME_/v1/endpoints`
  - View endpoints for an endpoint type: `https://yourcluster.com/service/_PKGNAME_/v1/endpoints/<endpoint>`

Returned endpoints will include the following:
- `.mesos` hostnames for each instance which will follow them if they're moved within the DC/OS cluster.
- A HA-enabled VIP hostname for accessing any of the instances (optional).
- A direct IP address for accesssing the service if `.mesos` hostnames are not resolvable.

In general, the `.mesos` endpoints will only work from within the same DC/OS cluster. From outside the cluster you may either use the direct IPs, or set up a proxy service which acts as a frontend to your _SERVICENAME_ instance. For development and testing purposes, you may use [DC/OS Tunnel](https://docs.mesosphere.com/latest/administration/access-node/tunnel/) to access services from outside the cluster, but this option is not suitable for production use.

## Connecting clients to endpoints

_GIVEN A RELEVANT EXAMPLE CLIENT FOR YOUR SERVICE, PROVIDE INSTRUCTIONS FOR CONNECTING THAT CLIENT USING THE ENDPOINTS LISTED ABOVE. WE RECOMMEND USING THE .MESOS ENDPOINTS IN YOUR EXAMPLE AS THEY WILL FOLLOW TASKS IF THEY ARE MOVED WITHIN THE CLUSTER._

<a name="managing"></a>
# Managing

## Updating Configuration
You may deploy changes to the service after it has been launched. Configuration management is handled by the Scheduler process, which in turn handles deploying _SERVICENAME_ itself.

Configuration changes may be performed by editing the runtime environment of the Scheduler. After making a change, the scheduler will be restarted, and it will automatically deploy any detected changes to the service, one node at a time. For example a given change will first be applied to `_NODEPOD_-0`, then `_NODEPOD_-1`, and so on.

Nodes are configured with a "Readiness check" to ensure that the underlying service appears to be in a healthy state before continuing with applying a given change to the next node in the sequence. However this basic check is not foolproof and reasonable care should be taken to ensure that a given configuration change will not negatively affect the behavior of the service.

Some changes, such as decreasing the number of nodes or changing volume requirements, are not supported after initial deployment. See [Limitations](#limitations).

To make configuration changes via scheduler environment updates, perform the following steps:
1. Visit http://yourcluster.com/ to view the DC/OS Dashboard.
1. Navigate to `Services` and click on the service to be configured (default _`PKGNAME`_).
1. Click `Edit` in the upper right. On DC/OS 1.9.x, the `Edit` button is obscured behind three dots.
1. Navigate to `Environment` (or `Environment variables`) and search for the option to be updated.
1. Update the option value and click `Review and run` (or `Deploy changes`).
1. The Scheduler process will be restarted with the new configuration, and will validate any detected changes.
1. If the detected changes pass validation, the relaunched Scheduler will deploy the changes by sequentially relaunching affected tasks as described above.

To see a full listing of available options, run `dcos package describe --config _PKGNAME_` in the CLI, or browse the _SERVICE NAME_ install dialog in the DC/OS Dashboard.

### Adding a Node
The service deploys _DEFAULT NODE COUNT_ nodes by default. This may be customized at initial deployment or after the cluster is already running. Shrinking the cluster is not supported.

Modify the `NODE_COUNT` environment variable to update the node count. Shrinking the cluster after initial deployment is not supported. If you decrease this value, the scheduler will complain about the configuration change until it's reverted back to its original value or larger.

### Resizing a Node
The CPU and Memory requirements of each node may be increased or decreased as follows:
- CPU (1.0 = 1 core): `NODE_CPUS`
- Memory (in MB): `NODE_MEM` _MENTION ANY OTHER ENVVARS THAT SHOULD BE ADJUSTED ALONG WITH THE MEMORY ENVVAR HERE?_

Note that volume requirements (type and/or size) may not be changed after initial deployment.

### _PER-NODE OPTIONS SPECIFIC TO YOUR IMPLEMENTATION GO HERE_

_ADD ONE OR MORE SECTIONS HERE TO DESCRIBE RE-CONFIGURATION OF HIGHLIGHTED NODE-SPECIFIC OPTIONS THAT YOUR SERVICE EXPOSES_

### Updating Placement Constraints

Placement constraints may be updated after initial deployment using the following procedure. See [Service Settings](#service-settings) above for more information on placement constraints.

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
1. Redeploy `_NODEPOD_-1` from the decommissioned node to somewhere within the new whitelist: `dcos _PKGNAME_ pods replace _NODEPOD_-1`
1. Wait for `_NODEPOD_-1` to be up and healthy before continuing with any other replacement operations.

### _SERVICE-WIDE OPTIONS SPECIFIC TO YOUR IMPLEMENTATION GO HERE_

_ADD ONE OR MORE SECTIONS HERE TO DESCRIBE RE-CONFIGURATION OF HIGHLIGHTED SERVICE-WIDE OPTIONS EXPOSED BY YOUR IMPLEMENTATION_

## Restarting nodes

This operation will restart a node, while keeping it at its current location and with its current persistent volume data. This may be thought of as similar to restarting a system process, but it also deletes any data which isn't in a persistent volume, via the magic of containers.

1. Run `dcos _PKGNAME_ pods restart _NODEPOD_-<NUM>`, e.g. `_NODEPOD_-2`.

## Replacing nodes

This operation will move a node to a new system, and will discard the persistent volumes at the prior system to be rebuilt at the new system. Perform this operation if a given system is about to be offlined or has already been offlined. Note that nodes are not moved automatically; you must manually perform the following steps to move nodes to new systems. You may build your own automation to perform node replacement automatically according to your own preferences.

1. _ANY STEPS TO WIND DOWN A NODE BEFORE IT'S WIPED/DECOMMISSIONED GO HERE_
1. Run `dcos _PKGNAME_ pods replace _NODEPOD_-<NUM>` to halt the current instance (if still running) and launch a new instance elsewhere.

For example, let's say `_NODEPOD_-3`'s host system has died and `_NODEPOD_-3` needs to be moved.

1. _DETAILED INSTRUCTIONS FOR WINDING DOWN A NODE, IF NEEDED FOR YOUR SERVICE, GO HERE_
1. _"NOW THAT THE NODE HAS BEEN DECOMMISSIONED," (IF NEEDED BY YOUR SERVICE)_ start `_NODEPOD_-3` at a new location in the cluster.
	```
	dcos _PKGNAME_ pods replace _NODEPOD_-3
	```

<a name="disaster-recovery"></a>
# Disaster Recovery

## Backup

_INSTRUCTIONS FOR BACKING UP DATA FROM YOUR SERVICE. CONSIDER SPECIFYING A SIDECAR TASK IN SVC.YML TO AUTOMATE THIS_

## Restore

_INSTRUCTIONS FOR RESTORING BACKED UP DATA TO YOUR SERVICE. CONSIDER SPECIFYING A SIDECAR TASK IN SVC.YML TO AUTOMATE THIS_

<a name="#deploy-best-practices"></a>
# Deployment Best Practices

- Run [backups](#disaster-recovery) on a regular basis, and test your backups.
- Configure alerting/monitoring of your service to detect downtime and other issues.
- If your cluster has been [configured with availability zones (e.g. Rack IDs)](https://github.com/dcos/dcos-docs/blob/51fe4641152e2c9361877439c40ddfeab61506e0/1.8/administration/faq.md#q-how-to-add-mesos-attributes-to-nodes-in-order-to-use-marathon-constraints), Placement Constraints may be used to map the service across those zones.

<a name="troubleshooting"></a>
# Troubleshooting

## Accessing Logs

Logs for the Scheduler and all service nodes may be browsed via the DC/OS Dashboard.

- Scheduler logs are useful for determining why a node isn't being launched (this is under the purview of the Scheduler).
- Node logs are useful for examining problems in the service itself.

In all cases, logs are generally piped to files named `stdout` and/or `stderr`.

To view logs for a given node, perform the following steps:
1. Visit http://yourcluster.com/ to view the DC/OS Dashboard.
1. Navigate to `Services` and click on the service to be examined (default _`PKGNAME`_).
1. In the list of tasks for the service, click on the task to be examined (scheduler is named after the service, nodes are each `_NODEPOD_-#-node`).
1. In the task details, click on the `Logs` tab to go into the log viewer. By default you will see `stdout`, but `stderr` is also useful. Use the pull-down in the upper right to select the file to be examined.

In case of problems with accessing the DC/OS Dashboard, logs may also be accessed via the Mesos UI:
1. Visit http://yourcluster.com/mesos to view the Mesos UI.
1. Click the `Frameworks` tab in the upper left to get a list of services running in the cluster.
1. Navigate into the correct Framework for your needs. The Scheduler runs under `marathon` with a task name matching the service name (default _`PKGNAME`_). Meanwhile service nodes run under a Framework whose name matches the service name (default _`PKGNAME`_).
1. You should now see two lists of tasks. `Active Tasks` are what's currently running, and `Completed Tasks` are what has since exited. Click on the `Sandbox` link for the task you wish to examine.
1. The `Sandbox` view will list files named `stdout` and `stderr`. Click the file names to view the files in the browser, or click `Download` to download them to your system for local examination. Note that very old tasks will have their Sandbox automatically deleted to limit disk space usage.

<a name="knownissues"></a>
# Known Issues

- _LIST ANY KNOWN BUGS OR ISSUES WITH YOUR SERVICE INTEGRATION (AND THEIR WORKAROUNDS) HERE_

<a name="limitations"></a>
# Limitations

- _LIST SUMMARY OF CAVEATS OR USEFUL KNOWLEDGE FOR RUNNING THE SERVICE HERE_
- Shrinking cluster size (number of nodes) is not supported.

## Removing a Node

Removing a node is not supported at this time.

## Automatic Failed Node Recovery

Nodes are not automatically replaced by the service in the event a system goes down. You may either manually replace nodes as described under [Managing](#managing), or build your own ruleset and automation to perform this operation automatically.

## Updating Storage Volumes

Neither volume type nor volume size requirements may be changed after initial deployment.

## Rack-aware Replication

Rack awareness within the service is not currently supported, but is planned to be supported with a future release of DC/OS.

## _ANY OTHER CAVEATS TO MENTION HERE?

_FOR EXAMPLE, DOES YOUR SERVICE REQUIRE MANUAL INVOLVEMENT BY THE USER IN CERTAIN SCENARIOS?_

<a name="terms"></a>
# Terms of Use

_ANY RISK WARNINGS OR REQUIREMENTS FOR SUPPORTED ENVIRONMENTS GO HERE_

<a name="support"></a>
# Support

## Supported Versions

- _SERVICENAME_: _WHAT VERSION OF YOUR SERVICE IS INCLUDED IN THE PACKAGE?_
- DC/OS: _LIST VERSION(S) OF DC/OS THAT YOU'VE TESTED AND SUPPORT_

## Package Versioning

Packages are versioned with an `a.b.c-x.y.z` format, where `a.b.c` is the version of the service management layer and `x.y.z` indicates the version of _SERVICENAME_. For example, `1.5.0-3.2.1` indicates version `1.5.0` of the service management layer and version `3.2.1` of _SERVICENAME_.

### Upgrades/downgrades

The package supports upgrade and rollback between adjacent versions only. For example, to upgrade from version 2 to version 4, you must first complete an upgrade to version 3, followed by an upgrade to version 4.

## Reaching Technical Support

_PLACES TO GET HELP GO HERE: MAILING LISTS? SLACK? SUPPORT CONTACTS?_
