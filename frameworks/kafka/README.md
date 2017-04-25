<p align="left"><img src="https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/dcos-sdk-logo.png" width="250"/></p>


# Apache Kafka Service Guide

<!-- Add TOC later if needed, once all sections are finalized -->

<a name="overview"></a>
# Overview

DC/OS Apache Kafka is an automated service that makes it easy to deploy and manage Apache Kafka on [DC/OS](https://mesosphere.com/product/). Apache Kafka is a distributed high-throughput publish-subscribe messaging system with strong ordering guarantees. Kafka clusters are highly available, fault tolerant, and very durable. For more information on Apache Kafka, see its [documentation](http://kafka.apache.org/documentation.html).

The service comes with a reasonable initial configuration for evaluation use. You can customize the service configuration at initial install, and later update once the service is already running through a configuration rollout process. If you just want to try out the service, you can use the default configuration and be up and running within moments.

Interoperating clients and services can take advantage of DC/OS service discovery features to directly access Apache Kafka via advertised endpoints, regardless of where the instance is currently located within a DC/OS Cluster.

You can install multiple Kafka instances on DC/OS and manage them independently. This allows different teams within an organization to have isolated instances of the service.

## Features

- Multiple instances sharing the same physical systems (requires custom port configuration).
- Vertical (resource) and horizontal (increase broker count) scaling.
- Easy redeployment to new systems upon scheduled or unscheduled outages.
- Consistent DNS addresses regardless of where brokers are located in the cluster.
- Node placement may be customized via Placement Constraints.

<a name="quick-start"></a>
# Quick Start

1. Install DC/OS on your cluster. See [the documentation](https://docs.mesosphere.com/latest/administration/installing/) for instructions.

1. If you are using open source DC/OS, install Kafka with the following command from the DC/OS CLI. If you are using Enterprise DC/OS, you may need to follow additional instructions. See the Install and Customize section for more information.

```
dcos package install kafka
```

1. . Kafka will deploy with a default configuration. You can monitor deployment at the Services tab of the DC/OS web interface.

	You can also install Kafka from [the DC/OS web interface](https://docs.mesosphere.com/latest/usage/webinterface/).

1. Connect a client to the service.
```
dcos kafka endpoints
[
  "zookeeper",
  "broker"
]

dcos kafka endpoints _ENDPOINT_
{
  "address": [
    "10.0.1.161:1025",
    "10.0.1.6:1025",
    "10.0.3.205:1025"
  ],
  "dns": [
    "kafka-2-broker.kafka.mesos:1025",
    "kafka-0-broker.kafka.mesos:1025",
    "kafka-1-broker.kafka.mesos:1025"
  ],
  "vip": "broker.kafka.l4lb.thisdcos.directory:9092"
}
```

<a name="installing-and-customizing"></a>
# Installing and Customizing

The default Kafka installation provides reasonable defaults for trying out the service, but may not be sufficient for production use. You may require different configurations depending on the context of the deployment.

## Prerequisities
- Depending on your security mode in Enterprise DC/OS, you may [need to provision a service account](/1.9/administration/id-and-access-mgt/service-auth/kafka-auth/) before installing Kafka. Only someone with `superuser` permission can create the service account.
	- `strict` [security mode](https://docs.mesosphere.com/1.9/administration/installing/custom/configuration-parameters/#security) requires a service account.  
	- `permissive` security mode a service account is optional.
	- `disabled` security mode does not require a service account.
- Your cluster must have at least three private nodes.

## Installation from the CLI

To start a basic test cluster with three brokers, run the following command on the DC/OS CLI. Enterprise DC/OS users must follow additional instructions. [More information about installing Kafka on Enterprise DC/OS](https://github.com/mesosphere/dcos-commons/blob/master/frameworks/kafka/docs/install-and-customize.md#install-enterprise).

```
dcos package install kafka
```

You can specify a custom configuration in an `options.json` file and pass it to `dcos package install` using the `--options` parameter.

```
$ dcos package install kafka --options=your-options.json
```

For more information about building the options.json file, see the [DC/OS documentation](https://docs.mesosphere.com/latest/usage/managing-services/config-universe-service/).

## Installation from the DC/OS Web Interface

You can [install Kafka from the DC/OS web interface](https://docs.mesosphere.com/1.9/usage/managing-services/install/). If you install Kafka from the web interface, you must install the Kafka DC/OS CLI subcommands separately. From the DC/OS CLI, enter:

```bash
dcos package install kafka --cli
```

Choose `ADVANCED INSTALLATION` to perform a custom installation.

## Service Settings

### Service Name

Each instance of Kafka in a given DC/OS cluster must be configured with a different service name. You can configure the service name in the **service** section of the advanced installation section of the DC/OS web interface. The default service name (used in many examples here) is `kafka`.

All DC/OS Kafka CLI commands have a --name argument that allows the user to specify which instance to query. If you do not specify a service name, the CLI assumes the default value, `kafka`. The default value for --name can be customized via the DC/OS CLI configuration:

```
dcos kafka --name kafka-dev <cmd>
```

## Broker Settings

Adjust the following settings to customize the amount of resources allocated to each broker. Kafka's requirements (http://kafka.apache.org/documentation.html) must be taken into consideration when adjusting these values. Reducing these values below those requirements may result in adverse performance and/or failures while using the service.

Each of the following settings may be customized under the **broker** configuration section of the advanced installation section of the DC/OS web interface.

### Broker Count

Configure the number of brokers running in a given Kafka cluster. The default count at installation is three brokers. This number may be increased, but not decreased, after installation.

### CPU

The amount of CPU allocated to each broker may be customized. A value of `1.0` equates to one full CPU core on a machine. Customize this value by editing the **cpus** value under the **broker** configuration section. Turning this too low will result in throttled tasks.

### Memory

The amount of RAM allocated to each broker may be customized. Customize this value by editing the **mem** value (in MB) under the **broker** configuration section. Turning this too low will result in out of memory errors.


### Ports

Each port exposed by the service may be customized in the service configuration. If you wish to install multiple instances of the service and have them colocate on the same machines, you must ensure that **no** ports are common between those instances. Customizing ports is only needed if you require multiple instances sharing a single machine. This customization is optional otherwise.

### Storage Volumes

The service supports two volume types:
- `ROOT` volumes are effectively an isolated directory on the root volume, sharing IO/spindles with the rest of the host system.
- `MOUNT` volumes are a dedicated device or partition on a separate volume, with dedicated IO/spindles.

Using `MOUNT` volumes requires [additional configuration on each DC/OS agent system](https://docs.mesosphere.com/1.9/administration/storage/mount-disk-resources/), so the service currently uses `ROOT` volumes by default. 

### Placement Constraints

Placement constraints allow you to customize where the service is deployed in the DC/OS cluster. 

Placement constraints support all [Marathon operators (reference)](http://mesosphere.github.io/marathon/docs/constraints.html) with this syntax: `field:OPERATOR[:parameter]`. For example, if the reference lists `[["hostname", "UNIQUE"]]`, you should  use `hostname:UNIQUE`.

A common task is to specify a list of whitelisted systems to deploy to. To achieve this, use the following syntax for the placement constraint:
```
hostname:LIKE:10.0.0.159|10.0.1.202|10.0.3.3
```

You must include spare capacity in this list, so that if one of the whitelisted systems goes down, there is still enough room to repair your service without that system.

For an example of updating placement constraints, see [Managing](#managing), below.


<a name="uninstalling"></a>
# Uninstalling

Follow these steps to uninstall the service.

1. Uninstall the service. From the DC/OS CLI, enter `dcos package uninstall`.
1. Clean up remaining reserved resources with the framework cleaner script, `janitor.py`. [More information about the framework cleaner script](https://docs.mesosphere.com/1.9/usage/managing-services/uninstall/#framework-cleaner).

To uninstall an instance named `kafka` (the default), run:
```
MY_SERVICE_NAME=kafka
dcos package uninstall --app-id=$MY_SERVICE_NAME kafka
dcos node ssh --master-proxy --leader "docker run mesosphere/janitor /janitor.py \
    -r $MY_SERVICE_NAME-role \
    -p $MY_SERVICE_NAME-principal \
    -z dcos-service-$MY_SERVICE_NAME"
```

<a name="connecting-clients"></a>
# Connecting clients

One of the benefits of running containerized services is that they can be placed anywhere in the cluster. Because they can be deployed anywhere on the cluster, clients need a way to find the service. This is where service discovery comes in.

## Discovering endpoints

Once the service is running, you can view information about its endpoints using either of the following methods:
- CLI:
  - List endpoint types: `dcos kafka endpoints`
  - View endpoints for an endpoint type: `dcos kafka endpoints <endpoint>`
- Web Interface:
  - List endpoint types: `https://<dcos-url>/service/kafka/v1/endpoints`
  - View endpoints for an endpoint type: `https://<dcos-url>/service/kafka/v1/endpoints/<endpoint>`

Returned endpoints will include the following:
- `.mesos` hostnames for each instance, which will follow them if they're moved within the DC/OS cluster.
- A HA-enabled VIP hostname for accessing any of the instances (optional).
- A direct IP address for accesssing the service if `.mesos` hostnames are not resolvable.

In general, the `.mesos` endpoints will only work from within the same DC/OS cluster. From outside the cluster you can either use the direct IPs or set up a proxy service that acts as a frontend to your Apache Kafka instance. For development and testing purposes, you can use [DC/OS Tunnel](https://docs.mesosphere.com/latest/administration/access-node/tunnel/) to access services from outside the cluster, but this option is not suitable for production use.


<a name="managing"></a>
# Managing

## Updating Configuration
You can make changes to the service after it has been launched. Configuration management is handled by the scheduler process, which in turn handles deploying Apache Kafka itself.

Edit the runtime environment of the scheduler to make configuration changes. After making a change, the scheduler will be restarted and automatically deploy any detected changes to the service.


Some changes, such as decreasing the number of brokers or changing volume requirements, are not supported after initial deployment. See [Limitations](#limitations).

To make configuration changes via scheduler environment updates, perform the following steps:

1. Visit https://<dcos-url> to access the DC/OS web interface.
1. Go to the `Services` and click the service to be configured (default `kafka`).
1. Click `Edit` in the upper right. On DC/OS 1.9.x, the `Edit` button is in a menu made up of three dots.
1. Navigate to `Environment` (or `Environment variables`) and search for the option to be updated.
1. Update the option value and click `Review and run` (or `Deploy changes`).
1. The scheduler process will be restarted with the new configuration, and will validate any detected changes.
1. If the detected changes pass validation, the relaunched Scheduler will deploy the changes by sequentially relaunching affected tasks as described above.

To see a full list of available options, run `dcos package describe --config kafka` in the CLI, or browse the Apache Kafka install dialog in the DC/OS web interface.

### Adding a Node
The service deploys `BROKER_COUNT` tasks by default. This may be customized at initial deployment or after the cluster is running. Shrinking the cluster is not supported.

### Resizing a Node
The CPU and Memory requirements of each broker may be increased or decreased as follows:
- CPU (1.0 = 1 core): `BROKER_CPUS`
- Memory (in MB): `BROKER_MEM` 

**Note:** Volume requirements (type and/or size) cannot be changed after initial deployment.

### Updating Placement Constraints

Placement constraints can be updated after initial deployment using the following procedure. See [Service Settings](#service-settings) for more information on placement constraints.

Let's say we have the following deployment of our brokers:

- Placement constraint of: `hostname:LIKE:10.0.10.3|10.0.10.8|10.0.10.26|10.0.10.28|10.0.10.84`
- Tasks:
```
10.0.10.3: kafka-0
10.0.10.8: kafka-1
10.0.10.26: kafka-2
10.0.10.28: empty
10.0.10.84: empty
```

`10.0.10.8` is being decommissioned and we should move away from it. Steps:

1. Remove the decommissioned IP and add a new IP to the placement rule whitelist by editing `PLACEMENT_CONSTRAINT`:

	```
	hostname:LIKE:10.0.10.3|10.0.10.26|10.0.10.28|10.0.10.84|10.0.10.123
	```
1. Redeploy `kafka-1` from the decommissioned broker to somewhere within the new whitelist: `dcos kafka pods replace kafka-1`.
1. Wait for `kafka-1` to be up and healthy before continuing with any other replacement operations.


## Restarting brokers

This operation will restart a broker while keeping it at its current location and with its current persistent volume data. This may be thought of as similar to restarting a system process, but it also deletes any data that is not on a persistent volume.

1. Run `dcos kafka pods restart kafka-0`

## Replacing brokers

This operation will move a broker to a new system and will discard the persistent volumes at the prior system to be rebuilt at the new system. Perform this operation if a given system is about to be offlined or has already been offlined. 

**Note:** Brokers are not moved automatically. You must manually perform the following steps to move tasks to new systems. You can build your own automation to perform broker replacement automatically according to your own preferences.

1. Run `dcos kafka pods replace kafka-0` to halt the current instance (if still running) and launch a new instance elsewhere.

For example, let's say `kafka-0`'s host system has died and `kafka-0` needs to be moved.

	```
	dcos kafka pods replace kafka-0
	```

<a name="disaster-recovery"></a>
# Disaster Recovery

## Backup/Restore
DC/OS Apache Kafka does not natively support any backup or restore functionality.  For more information on Apache Kafka backup/restore, please see its [documentation](http://kafka.apache.org/documentation.html)
  
<a name="troubleshooting"></a>
# Troubleshooting

## Accessing Logs

You can find logs for the scheduler and all service pods on the the DC/OS web interface.

- Scheduler logs are useful for determining why a pod isn't being launched (this is under the purview of the scheduler).
- Pod logs are useful for examining problems in the service itself.

In all cases, logs are generally piped to files named `stdout` and/or `stderr`.

To view logs for a given pod, perform the following steps:
1. Visit `https://<dcos-url>` to view the DC/OS Dashboard.
1. Navigate to `Services` and click the service to be examined (default `kafka`).
1. In the list of tasks for the service, click the task to be examined.
1. In the task details, click the `Logs` tab to go into the log viewer. By default you will see `stdout`, but `stderr` is also useful. Use the pull-down in the upper right to select the file to be examined.

You can also access the logs via the Mesos UI:
1. Visit `https://<dcos-url>/mesos` to view the Mesos UI.
1. Click the `Frameworks` tab in the upper left to get a list of services running in the cluster.
1. Navigate to the correct framework for your needs. The scheduler runs under `marathon` with a task name matching the service name (default `kafka`). Service pods run under a framework whose name matches the service name (default `kafka`).
1. You should see two lists of tasks. `Active Tasks` are tasks currently running and `Completed Tasks` are tasks that have exited. Click the `Sandbox` link for the task you wish to examine.
1. The `Sandbox` view will list files named `stdout` and `stderr`. Click the file names to view the files in the browser, or click `Download` to download them to your system for local examination. Note that very old tasks will have their Sandbox automatically deleted to limit disk space usage.


<a name="limitations"></a>
# Limitations

- Shrinking cluster size (number of brokers) is not supported.

## Automatic Failed Node Recovery

Nodes are not automatically replaced by the service in the event a system goes down. You may either manually replace pods as described under [Managing](#managing), or build your own ruleset and automation to perform this operation automatically.

## Updating Storage Volumes

Neither volume type nor volume size requirements may be changed after initial deployment.

## Rack-aware Replication

Rack awareness within the service is not currently supported, but is planned to be supported with a future release of DC/OS.


<a name="support"></a>
# Support

Enterprise DC/OS customers may submit support cases via support@mesosphere.com.

## Supported Versions

- Apache Kafka: 0.10.1.0
- DC/OS: 1.8 and 1.9

## Package Versioning

Packages are versioned with an `a.b.c-x.y.z` format, where `a.b.c` is the version of the service management layer and `x.y.z` indicates the version of Apache Kafka. For example, `1.1.20-0.10.1.0` indicates version `1.1.20` of the service management layer and version `0.10.1.0` of Apache Kafka.

### Upgrades/downgrades

The package supports upgrade and rollback between adjacent versions only. For example, to upgrade from version 2 to version 4, you must first complete an upgrade to version 3, followed by an upgrade to version 4.


