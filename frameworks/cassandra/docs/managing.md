---
post_title: Managing
menu_order: 60
post_excerpt: ""
enterprise: 'no'
---

# Updating Configuration

You can make changes to the service after it has been launched. Configuration management is handled by the scheduler process, which in turn handles deploying Cassandra itself.

After making a change, the scheduler will be restarted, and it will automatically deploy any detected changes to the service, one node at a time. For example, a given change will first be applied to the `node-0` pod, then `node-1`, and so on.

Nodes are configured with a "Readiness check" to ensure that the underlying service appears to be in a healthy state before continuing with applying a given change to the next node in the sequence. However, this basic check is not foolproof and reasonable care should be taken to ensure that a given configuration change will not negatively affect the behavior of the service.

Some changes, such as decreasing the number of nodes or changing volume requirements, are not supported after initial deployment. See [Limitations](#limitations).

The instructions below describe how to update the configuration for a running DC/OS service.

### Enterprise DC/OS 1.10

Enterprise DC/OS 1.10 introduces a convenient command line option that allows for easier updates to a service's configuration, as well as allowing users to inspect the status of an update, to pause and resume updates, and to restart or complete steps if necessary.

#### Prerequisites

+ Enterprise DC/OS 1.10 or newer.
+ Service with a version greater than 2.0.0-x.
+ [The DC/OS CLI](https://docs.mesosphere.com/latest/cli/install/) installed and available.
+ The service's subcommand available and installed on your local machine.
  + You can install just the subcommand CLI by running `dcos package install --cli beta-cassandra`.
  + If you are running an older version of the subcommand CLI that doesn't have the `update` command, uninstall and reinstall your CLI.
    ```bash
    dcos package uninstall --cli beta-cassandra
    dcos package install --cli beta-cassandra
    ```

#### Preparing configuration

If you installed this service with Enterprise DC/OS 1.10, you can fetch the full configuration of a service (including any default values that were applied during installation). For example:

```bash
$ dcos beta-cassandra describe > options.json
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
$ SERVICE_NAME=beta-cassandra
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
$ dcos beta-cassandra update start --options=options.json
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

To see a full listing of available options, run `dcos package describe --config beta-cassandra` in the CLI, or browse the DC/OS Apache Cassandra Service install dialog in the DC/OS Dashboard.

## Adding a Node

The service deploys 3 nodes by default. This may be customized at initial deployment or after the cluster is already running via the `NODES` environment variable. Shrinking the cluster is not supported. If you decrease this value, the scheduler will complain about the configuration change until it's reverted back to its original value or a larger one.

## Resizing a Node

The CPU and Memory requirements of each node may be increased or decreased as follows:
- CPU (1.0 = 1 core): `CASSANDRA_CPUS`
- Memory (in MB): `CASSANDRA_MEMORY_MB`. To prevent out of memory errors, you must ensure that the `TASKCFG_ALL_CASSANDRA_HEAP_SIZE` environment variable is less than `$CASSANDRA_MEMORY_MB`.

Note: volume requirements (type and/or size) can not be changed after initial deployment.

## Updating Placement Constraints

Placement constraints may be updated after initial deployment using the following procedure. See [Service Settings](#service-settings) above for more information on placement constraints.

Let's say we have the following deployment of our nodes

- Placement constraint of: `hostname:LIKE:10.0.10.3|10.0.10.8|10.0.10.26|10.0.10.28|10.0.10.84`
- Tasks:
```
10.0.10.3: node-0
10.0.10.8: node-1
10.0.10.26: node-2
10.0.10.28: empty
10.0.10.84: empty
```

`10.0.10.8` is being decommissioned and we should move away from it. Steps:

1. Remove the decommissioned IP and add a new IP to the placement rule whitelist by editing `PLACEMENT_CONSTRAINT`:

	```
	hostname:LIKE:10.0.10.3|10.0.10.26|10.0.10.28|10.0.10.84|10.0.10.123
	```
1. Redeploy `node-1` from the decommissioned node to somewhere within the new whitelist: `dcos beta-cassandra pod replace node-1`
1. Wait for `node-1` to be up and healthy before continuing with any other replacement operations.

# Restarting a Node

This operation will restart a node, while keeping it at its current location and with its current persistent volume data. This may be thought of as similar to restarting a system process, but it also deletes any data that is not on a persistent volume.

1. Run `dcos beta-cassandra pod restart node-<NUM>`, e.g. `node-2`.

# Replacing a Node

This operation will move a node to a new system and will discard the persistent volumes at the prior system to be rebuilt at the new system. Perform this operation if a given system is about to be offlined or has already been offlined.

**Note:** Nodes are not moved automatically. You must perform the following steps manually to move nodes to new systems. You can automate node replacement according to your own preferences.

1. Run `dcos beta-cassandra pod replace node-<NUM>` to halt the current instance with id `<NUM>` (if still running) and launch a new instance elsewhere.

For example, let's say `node-2`'s host system has died and `node-2` needs to be moved.
```
dcos beta-cassandra pod replace node-2
```

## Seed nodes
Cassandra seed nodes are those nodes with indices smaller than the seed node count.  By default, Cassandra is deployed
with a seed node count of two.  So, node-0 and node-1 are seed nodes. When a replace operation is performed on one these
nodes, all other nodes must be restarted to be brought up to date regarding the ip address of the new seed node. This
operation is performed automatically.

For example if `node-0` needed to be replaced we would execute:

```bash
dcos beta-cassandra pod replace node-0
```

which would result in a recovery plan like the following:

```bash
$ dcos beta-cassandra --name=cassandra plan show recovery
recovery (IN_PROGRESS)
└─ permanent-node-failure-recovery (IN_PROGRESS)
   ├─ node-0:[server] (COMPLETE)
   ├─ node-1:[server] (STARTING)
   └─ node-2:[server] (PENDING)

```

**Note:** Only the seed node is being placed on a new node, all other nodes are restarted in place with no loss of data.

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

# Upgrading Service Version

The instructions below show how to safely update one version of a service to the next.

## Viewing available versions

The `update package-versions` command allows you to view the versions of a service that you can upgrade or downgrade to. These are specified by the service maintainer and depend on the semantics of the service (i.e. whether or not upgrades are reversal).

For example, for DC/OS Apache Cassandra Service, run:
```bash
$ dcos beta-cassandra update package-versions
```

## Upgrading or downgrading a service

1. Before updating the service itself, update its CLI subcommand to the new version:
```bash
$ dcos package uninstall --cli beta-cassandra
$ dcos package install --cli beta-cassandra --package-version="1.1.6-5.0.7"
```
1. Once the CLI subcommand has been updated, call the update start command, passing in the version. For example, to update DC/OS Apache Cassandra Service to version `1.1.6-5.0.7`:
```bash
$ dcos beta-cassandra update start --package-version="1.1.6-5.0.7"
```

If you are missing mandatory configuration parameters, the `update` command will return an error. To supply missing values, you can also provide an `options.json` file (see [Updating configuration](#updating-configuration)):
```bash
$ dcos beta-cassandra update start --options=options.json --package-version="1.1.6-5.0.7"
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
$ dcos beta-cassandra update status
```

If the Scheduler is still restarting, DC/OS will not be able to route to it and this command will return an error message. Wait a short while and try again. You can also go to the Services tab of the DC/OS GUI to check the status of the restart.

## Pause

To pause an ongoing update, issue a pause command:

```bash
$ dcos beta-cassandra update pause
```

You will receive an error message if the plan has already completed or has been paused. Once completed, the plan will enter the `WAITING` state.

## Resume

If a plan is in a `WAITING` state, as a result of being paused or reaching a breakpoint that requires manual operator verification, you can use the `resume` command to continue the plan:

```bash
$ dcos beta-cassandra update resume
```

You will receive an error message if you attempt to `resume` a plan that is already in progress or has already completed.

## Force Complete

In order to manually "complete" a step (such that the Scheduler stops attempting to launch a task), you can issue a `force-complete` command. This will instruct to Scheduler to mark a specific step within a phase as complete. You need to specify both the phase and the step, for example:

```bash
$ dcos beta-cassandra update force-complete service-phase service-0:[node]
```

## Force Restart

Similar to force complete, you can also force a restart. This can either be done for an entire plan, a phase, or just for a specific step.

To restart the entire plan:
```bash
$ dcos beta-cassandra update force-restart
```

Or for all steps in a single phase:
```bash
$ dcos beta-cassandra update force-restart service-phase
```

Or for a specific step within a specific phase:
```bash
$ dcos beta-cassandra update force-restart service-phase service-0:[node]
```

<!-- END DUPLICATE BLOCK -->
