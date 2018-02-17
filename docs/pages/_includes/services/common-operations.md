This guide has so far focused on describing the components, how they work, and how to interact with them. At this point we'll start looking at how that knowledge can be applied to a running service.

## Initial service configuration

The DC/OS package format allows packages to define user-visible installation options. To ensure consistent installations, we recommend exporting the options you use into an `options.json` file, which can then be placed in source control and kept up to date with the current state of the cluster. Keeping these configurations around will make it easy to duplicate or reinstall services using identical configurations.

Use this CLI command to see what options are available for a given package:

```bash
$ dcos package describe elastic --config
{
  "properties": {
    "coordinator_nodes": {
      "description": "Elasticsearch coordinator node configuration properties",
      "properties": {
        "count": {
          "default": 1,
          "description": "Number of coordinator nodes to run",
          "minimum": 0,
          "type": "integer"
        },
        "cpus": {
          "default": 1.0,
          "description": "Node cpu requirements",
          "type": "number"
        },
        ...
      }
    }
    "service": {
      "description": "DC/OS service configuration properties",
      "properties": {
        ...
        "name": {
          "default": "elastic",
          "description": "The name of the Elasticsearch service instance",
          "type": "string"
        },
        ...
        "user": {
          "default": "core",
          "description": "The user that runs the Elasticsearch services and owns the Mesos sandbox.",
          "type": "string"
        }
      }
    }
  }
}
...
```

Given the above example, let's build an `elastic-prod-options.json` that customizes the above values:

```json
{
  "coordinator_nodes": {
    "count": 3,
    "cpus": 2.0
  },
  "service": {
    "name": "elastic-prod",
    "user": "elastic"
  }
}
```

Now that we have `elastic-prod-options.json`, we can install a service instance that uses it as follows:

```bash
$ dcos package install --options=elastic-prod-options.json elastic
```

Once we know the configuration is good, it should be added to our source control for tracking.

## Updating service configuration

Above, we described how a configuration update (including updating the version of the service) is handled. Now we will quickly show the steps to perform such an update.

Configuration updates are performed by updating the process environment of the Scheduler. Once restarted, the Scheduler will observe this change and re-deploy nodes as described in [Reconfiguration](#Reconfiguration).

### Enterprise DC/OS 1.10

Enterprise DC/OS 1.10 introduces a convenient command line option that allows for easier updates to a service's configuration, as well as allowing users to inspect the status of an update, to pause and resume updates, and to restart or complete steps if necessary.

#### Prerequisites

+ Enterprise DC/OS 1.10 or newer.
+ A DC/OS SDK-based service with a version greater than 2.0.0-x.
+ [The DC/OS CLI](https://docs.mesosphere.com/latest/cli/install/) installed and available.
+ The service's subcommand available and installed on your local machine.
  + You can install just the subcommand CLI by running `dcos package install --cli <service-name>`.
  + If you are running an older version of the subcommand CLI that doesn't have the `update` command, uninstall and reinstall your CLI.
    ```bash
    dcos package uninstall --cli <service-name>
    dcos package install --cli <service-name>
    ```

##### Updating package version

The instructions below show how to safely update one version of a service to the next.

###### Viewing available versions

The `update package-versions` command allows you to view the versions of a service that you can upgrade or downgrade to. These are specified by the service maintainer and depend on the semantics of the service (i.e. whether or not upgrades are reversal).

For example, for `dse`, run:
```bash
$ dcos dse update package-versions
```

###### Upgrading or downgrading a service

1. Before updating the service itself, update its CLI subcommand to the new version:
```bash
$ dcos package uninstall --cli dse
$ dcos package install --cli dse --package-version="1.1.6-5.0.7"
```
1. Once the CLI subcommand has been updated, call the update start command, passing in the version. For example, to update `dse` to version `1.1.6-5.0.7`:
```bash
$ dcos dse update start --package-version="1.1.6-5.0.7"
```

If you are missing mandatory configuration parameters, the `update` command will return an error.

To supply missing configuration values or to override configuration values, you can also provide an `options.json` file (see [Updating configuration](#updating-configuration) below):
```bash
$ dcos dse update start --options=options.json --package-version="1.1.6-5.0.7"
```

The default behavior on update is to merge ‘Default’, ‘Stored’ and ‘Provided’ configurations, in that order, and then
validate against the schema. In some situations, such as when a schema option has been removed, the default behavior
might result in an invalid configuration. You can work around this with `--replace=true` which, when specified,
will override the ‘Stored’ options with the ‘Provided’ options.
```bash
$ dcos dse update start --options=options.json --replace=true --package-verion="1.1.6-5.0.7"
```

See [Advanced update actions](#advanced-update-actions) for commands you can use to inspect and manipulate an update after it has started.

##### Updating configuration

The instructions below describe how to update the configuration for a running DC/OS service.

###### Preparing configuration

If you installed this service with Enterprise DC/OS 1.10, you can fetch the full configuration of a service (including any default values that were applied during installation). For example, for `dse`:

```bash
$ dcos dse describe > options.json
```

Make any configuration changes to this `options.json` file.

If you installed this service with a prior version of DC/OS, this configuration will not have been persisted by the DC/OS package manager. You can instead use the `options.json` file that was used when [installing the service](#initial-service-configuration).

<strong>Note:</strong> You need to specify all configuration values in the `options.json` file when performing a configuration update. Any unspecified values will be reverted to the default values specified by the DC/OS service. See the "Recreating `options.json`" section below for information on recovering these values.

####### Recreating `options.json` (optional)

If the `options.json` from when the service was last installed or updated is not available, you will need to manually recreate it using the following steps.

First, we'll fetch the default application's environment, current application's environment, and the actual template that maps config values to the environment:

1. Ensure you have [jq](https://stedolan.github.io/jq/) installed.
1. Set the service name that you're using, in this example we'll use `dse`:
```bash
$ SERVICE_NAME=dse
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
1. Use the variable names (e.g. `{{=<% %>=}}{{service.name}}<%={{ }}=%>`) to create a new `options.json` file as described in [Initial service configuration](#initial-service-configuration).

###### Starting the update

Once you are ready to begin, initiate an update using the DC/OS CLI, passing in the updated `options.json` file:

```bash
$ dcos dse update start --options=options.json
```

You will receive an acknowledgement message and the DC/OS package manager will restart the Scheduler in Marathon.

See [Advanced update actions](#advanced-update-actions) for commands you can use to inspect and manipulate an update after it has started.

##### Advanced update actions

The following sections describe advanced commands that be used to interact with an update in progress.

###### Monitoring the update

Once the Scheduler has been restarted, it will begin a new deployment plan as individual pods are restarted with the new configuration. Depending on the high availability characteristics of the service being updated, you may experience a service disruption.

You can query the status of the update as follows:

```bash
$ dcos dse update status
```

If the Scheduler is still restarting, DC/OS will not be able to route to it and this command will return an error message. Wait a short while and try again. You can also go to the Services tab of the DC/OS GUI to check the status of the restart.

###### Pause

To pause an ongoing update, issue a pause command:

```bash
$ dcos dse update pause
```

You will receive an error message if the plan has already completed or has been paused. Once completed, the plan will enter the `WAITING` state.

###### Resume

If a plan is in a `WAITING` state, as a result of being paused or reaching a breakpoint that requires manual operator verification, you can use the `resume` command to continue the plan:

```bash
$ dcos dse update resume
```

You will receive an error message if you attempt to `resume` a plan that is already in progress or has already completed.

###### Force Complete

In order to manually "complete" a step (such that the Scheduler stops attempting to launch a task), you can issue a `force-complete` command. This will instruct to Scheduler to mark a specific step within a phase as complete. You need to specify both the phase and the step, for example:

```bash
$ dcos dse update force-complete dse-phase dse-0:[node]
```

###### Force Restart

Similar to force complete, you can also force a restart. This can either be done for an entire plan, a phase, or just for a specific step.

To restart the entire plan:
```bash
$ dcos dse update force-restart
```

Or for all steps in a single phase:
```bash
$ dcos dse update force-restart dse-phase
```

Or for a specific step within a specific phase:
```bash
$ dcos dse update force-restart dse-phase dse-0:[node]
```

#### Open Source DC/OS, DC/OS 1.9, and Earlier

If you do not have Enterprise DC/OS 1.10 or later, the CLI commands above are not available. For Open Source DC/OS of any version, or Enterprise DC/OS 1.9 and earlier, you can perform changes from the DC/OS GUI.

1. Go to the **Services** tab of the DC/OS GUI and click the name of the Scheduler you wish to edit.

1. Click the three dots on the right hand side of the page for your Scheduler, then choose **Edit**.

[<img src="/dcos-commons/img/services/ops-guide-edit-scheduler.png" alt="Choose edit from the three dot menu" width="400"/>](/dcos-commons/img/services/ops-guide-edit-scheduler.png)

1. In the window that appears, click the **Environment** tab to show a list of the Scheduler's environment variables. For the sake of this demo, we will increase the `OPSCENTER_MEM` value from `4000` to `5000`, thereby increasing the RAM quota for the OpsCenter task in this service. See [finding the correct environment variable](#finding-the-correct-environment-variable) for more information on determining the correct value to be updated.

1. After you click `Change and deploy`, the following will happen:
   - Marathon will restart the Scheduler so that it picks up our change.
   - The Scheduler will detect that the OpsCenter task's configuration has changed. The OpsCenter task will be restarted with the change applied. In this case, with allocated RAM increased from 4000 to 5000 MB.

1. We can see the result by looking at the Mesos task list. At the top we see the new `dse` Scheduler and new OpsCenter instance. At the bottom we see the previous `dse` Scheduler and OpsCenter instance which were replaced due to our change:

[<img src="/dcos-commons/img/services/ops-guide-mesos-tasks-reconfigured.png" alt="dse app deployment in Mesos with exited tasks and newly launched tasks" width="400"/>](/dcos-commons/img/services/ops-guide-mesos-tasks-reconfigured.png)

   If we look at the Scheduler logs, we can even see where it detected the change. The `api-port` value is random on each Scheduler restart, so it tends to always display as 'different' in this log. Because of this, the Scheduler automatically ignores changes to `api-port`, and so the change can be ignored here:

```
INFO  2017-04-25 20:26:08,343 [main] com.mesosphere.sdk.config.DefaultConfigurationUpdater:printConfigDiff(215): Difference between configs:
--- ServiceSpec.old
+++ ServiceSpec.new
@@ -3,5 +3,5 @@
   "role" : "dse-role",
   "principal" : "dse-principal",
-  "api-port" : 18446,
+  "api-port" : 15063,
   "web-url" : null,
   "ZooKeeper" : "master.mesos:2181",
@@ -40,5 +40,5 @@
             "type" : "SCALAR",
             "scalar" : {
-              "value" : 4000.0
+              "value" : 5000.0
             },
             "ranges" : null,
```

The steps above apply to any configuration change: the Scheduler is restarted, detects the config change, and then launches and/or restarts any affected tasks to reflect the change. When multiple tasks are affected, the Scheduler will follow the deployment Plan used for those tasks to redeploy them. In practice this typically means that each task will be deployed in a sequential rollout, where task `N+1` is only redeployed after task `N` appears to be healthy and ready after being relaunched with the new configuration. Some services may have defined a custom `update` plan which invokes custom logic for rolling out changes which varies from the initial deployment rollout. The default behavior, when no custom `update` plan was defined, is to use the `deploy` plan.

##### Finding the correct environment variable

While DC/OS Enterprise 1.10+ supports changing the configuration using the option schema directly, DC/OS Open and versions 1.9 and earlier require mapping those options to the environment variables that are passed to the Scheduler.

The correct environment variable for a given setting can vary depending on the service. For instance, some services have multiple types of nodes, each with separate count settings. If you want to increase the number of nodes, it would take some detective work to find the correct environment variable.

For example, let's look at the most recent release of `confluent-kafka` as of this writing. The number of brokers is configured using a [`count` setting in the `brokers` section](https://github.com/mesosphere/universe/blob/98a21f4f3710357a235f0549c3caabcab66893fd/repo/packages/C/confluent-kafka/16/config.json#L133):

```json
{
  "...": "...",
  "count": {
    "description":"Number of brokers to run",
    "type":"number",
    "default":3
  },
  "...": "..."
}
```

To see where this setting is passed when the Scheduler is first launched, we can look at the adjacent [`marathon.json.mustache` template file](https://github.com/mesosphere/universe/blob/98a21f4f3710357a235f0549c3caabcab66893fd/repo/packages/C/confluent-kafka/16/marathon.json.mustache#L34). Searching for `brokers.count` in `marathon.json.mustache` reveals the environment variable that we should change:

```json
{
  "...": "...",
  "env": {
    "...": "...",
    "BROKER_COUNT": "{{=<% %>=}}{{brokers.count}}<%={{ }}=%>",
    "...": "..."
  },
  "...": "..."
}
```

This method can be used mapping any configuration setting (applicable during initial install) to its associated Marathon environment variable (applicable during reconfiguration).

### Uninstall

The uninstall flow was simplified for users as of DC/OS 1.10. The steps to uninstall a service therefore depends on the version of DC/OS:

#### DC/OS 1.10 and newer

If you are using DC/OS 1.10 and the installed service has a version greater than 2.0.0-x:

1. Uninstall the service. From the DC/OS CLI, enter `dcos package uninstall --app-id=<instancename> <packagename>`.

For example, to uninstall a Confluent Kafka instance named `kafka-dev`, run:

```bash
dcos package uninstall --app-id=kafka-dev confluent-kafka
```

#### Older versions

If you are running DC/OS 1.9 or older, or a version of the service that is older than 2.0.0-x, follow these steps:

1. Stop the service. From the DC/OS CLI, enter `dcos package uninstall --app-id=<instancename> <packagename>`.
   For example, `dcos package uninstall --app-id=kafka-dev confluent-kafka`.
1. Clean up remaining reserved resources with the `janitor.py` script. See [DC/OS documentation](https://docs.mesosphere.com/1.10/deploying-services/uninstall/#framework-cleaner) for more information about the cleaner script.

For example, to uninstall a Confluent Kafka instance named `kafka-dev`, run:

```bash
$ MY_SERVICE_NAME=kafka-dev
$ dcos package uninstall --app-id=$MY_SERVICE_NAME confluent-kafka`.
$ dcos node ssh --master-proxy --leader "docker run mesosphere/janitor /janitor.py \
    -r $MY_SERVICE_NAME-role \
    -p $MY_SERVICE_NAME-principal \
    -z dcos-service-$MY_SERVICE_NAME"
```

### Pod operations

Most operations for maintaining a service will involve interacting with and manipulating its [Pods](#pods).

#### Add or Remove a pod

Adding or removing pod instances within the service is treated as a configuration change, not a command.

In this case, we're increasing a pod count value, as provided by the service's configuration schema. In the case of the above `dse` service, we need to increase the configured `dsenode.count` from `3` (the default) to `4`. After the change, the Scheduler will deploy a new DSE node instance without changing the preexisting nodes.

For safety reasons, pod instances cannot be removed after they have been deployed by default. However, some services may allow some pods to be removed in cases where doing so is not a problem. To remove pod instances, you would simply decrease the count value, and then instances exceeding that count will be removed automatically in reverse order. For example, if you decreased `dsenode.count` from `4` to `2` and this was allowed by the DSE service, you would see `dsenode-3` be removed followed by `dsenode-2`, leaving only `dsenode-0` and `dsenode-1` still running. If the DSE service doesn't allow the number of instances to be decreased, the Scheduler would instead reject the decrease and show a validation error in its [deploy Plan](#status).

#### Restart a pod

Restarting a pod keeps it in the current location and leaves data in any persistent volumes as-is. Data outside of those volumes is reset via the restart. Restarting a pod may be useful if an underlying process is broken in some way and just needs a kick to get working again. For more information see [Recovery](#recovery-plan).

Restarting a pod can be done either via the CLI or via the underlying Scheduler API. Both forms use the same [API](http://mesosphere.github.io/dcos-commons/reference/swagger-api/). In these examples we list the known pods, and then restart the one named `dse-1`, which contains tasks named `dse-1-agent` and `dse-1-node`:

Via the CLI:

```bash
$ dcos datastax-dse --name=mydse pod list
[
  "dse-0",
  "dse-1",
  "dse-2",
  "opscenter-0",
  "studio-0"
]
$ dcos datastax-dse --name=mydse pod restart dse-1
{
  "pod": "dse-1",
  "tasks": [
    "dse-1-agent",
    "dse-1-node"
  ]
}
```

Via the HTTP API directly:

```bash
$ curl -k -H "Authorization: token=$(dcos config show core.dcos_acs_token)" <dcos-url>/service/dse/v1/pod
[
  "dse-0",
  "dse-1",
  "dse-2",
  "opscenter-0",
  "studio-0"
]
$ curl -k -X POST -H "Authorization: token=$(dcos config show core.dcos_acs_token)" <dcos-url>/service/dse/v1/pod/dse-1/restart
{
  "pod": "dse-1",
  "tasks": [
    "dse-1-agent",
    "dse-1-node"
  ]
}
```

All tasks within the pod are restarted as a unit. The response lists the names of the two tasks that were members of the pod.

#### Replace a pod

Replacing a pod discards all of its current data and moves it to a new random location in the cluster. As of this writing, you can technically end up replacing a pod and have it go back where it started. Replacing a pod may be useful if an agent machine has gone down and is never coming back, or if an agent is about to undergo downtime.

Pod replacement is not currently done automatically by the SDK, as making the correct decision requires operator knowledge of cluster status. Is a node really dead, or will it be back in a couple minutes? However, operators are free to build their own tooling to make this decision and invoke the replace call automatically. For more information see [Recovery](#recovery-plan).

As with restarting a pod, replacing a pod can be done either via the CLI or by directly invoking the HTTP API. The response lists all the tasks running in the pod which were replaced as a result:

```bash
$ dcos datastax-dse --name=mydse pod replace dse-1
{
  "pod": "dse-1",
  "tasks": [
    "dse-1-agent",
    "dse-1-node"
  ]
}
```

```bash
$ curl -k -X POST -H "Authorization: token=$(dcos config show core.dcos_acs_token)" http://yourcluster.com/service/dse/v1/pod/dse-1/replace
{
  "pod": "dse-1",
  "tasks": [
    "dse-1-agent",
    "dse-1-node"
  ]
}
```

#### Pause a pod

Pausing a pod relaunches it in an idle command state. This allows the operator to debug the contents of the pod, possibly making changes to fix problems. While these problems are often fixed by just replacing the pod, there may be cases where an in-place repair or other operation is needed.

For example:
- A pod which crashes immediately upon starting may need additional work to be performed.
- Some services may _require_ that certain repair operations be performed manually when the task itself isn't running.
Being able to put the pod in an offline but accessible state makes it easier to resolve these situations.

After the pod has been paused, it may be started again, at which point it will be restarted and will resume running task(s) where it left off.

Here is an example session where an `index-1` pod is crash looping due to some corrupted data in a persistent volume. The operator pauses the `index-1` pod, then uses `task exec` to repair the index. Following this, the operator starts the pod and it resumes normal operation:

```bash
$ dcos myservice debug pod pause index-1
{
  "pod": "index-1",
  "tasks": [
    "index-1-agent",
    "index-1-node"
  ]
}

$ dcos myservice pod status
myservice
├─ index
│  ├─ index-0
│  │  ├─ index-0-agent (COMPLETE)
│  │  └─ index-0-node (COMPLETE)
│  └─ index-1
│     ├─ index-1-agent (PAUSING)
│     └─ index-1-node (PAUSING)
└─ data
   ├─ data-0
   │  └─ data-0-node (COMPLETE)
   └─ data-1
      └─ data-1-node (COMPLETE)

... repeat "pod status" until index-1 tasks are PAUSED ...

$ dcos task exec --interactive --tty index-1-node /bin/bash
index-1-node$ ./repair-index && exit

$ dcos myservice debug pod resume index-1
{
  "pod": "index-1",
  "tasks": [
    "index-1-agent",
    "index-1-node"
  ]
}

$ dcos myservice pod status
myservice
├─ index
│  ├─ index-0
│  │  ├─ index-0-agent (RUNNING)
│  │  └─ index-0-node (RUNNING)
│  └─ index-1
│     ├─ index-1-agent (STARTING)
│     └─ index-1-node (STARTING)
└─ data
   ├─ data-0
   │  └─ data-0-node (RUNNING)
   └─ data-1
      └─ data-1-node (RUNNING)

... repeat "pod status" until index-1 tasks are RUNNING ...
```

In the above example, all tasks in the pod were being paused and started, but it's worth noting that the commands also support pausing and starting individual tasks within a pod. For example, `dcos myservice debug pod pause index-1 -t agent` will pause only the `agent` task within the `index-1` pod.

### Plan Operations

This lists available commands for viewing and manipulating the [Plans](#plans) used by the Scheduler to perform work against the underlying service.

#### List
Show all plans for this service.

```bash
dcos kakfa plan list
```

#### Status
Display the status of the plan with the provided plan name.

```bash
dcos kafka plan status deploy
```

**Note:** The `--json` flag, though not default, is helpful in extracting phase UUIDs. Using the UUID instead of name for a
phase is a more ensures that the request, ie to pause or force-complete, is exactly the phase intended.

#### Start
Start the plan with the provided name and any optional plan arguments.

```bash
dcos kafka plan start deploy
```

#### Stop
Stop the running plan with the provided name.

```bash
dcos kafka plan stop deploy
```

Plan Pause differs from Plan Stop in the following ways:
* Pause can be issued for a specific phase or for all phases within a plan. Stop can only be issued for a plan.
* Pause updates the underlying Phase/Step state. Stop not only updates the underlying state, but also restarts the plan.

#### Pause
Pause the plan, or a specific phase in that plan with the provided phase name (or UUID).

```bash
dcos kafka plan pause deploy 97e70976-505f-4689-abd2-6286c4499091
```

**NOTE:** The UUID above is an example. Use the Plan Status command with the `--json` flag to extract a valid UUID.

Plan Pause differs from Plan Stop in the following ways:
* Pause can be issued for a specific phase or for all phases within a plan. Stop can only be issued for a plan.
* Pause updates the underlying Phase/Step state. Stop not only updated the underlying state, but also restarts the plan.

#### Resume
Resume the plan, or a specific phase in that plan, with the provided phase name (or UUID).

```bash
dcos kafka plan resume deploy 97e70976-505f-4689-abd2-6286c4499091
```

#### Force-Restart
Restart the plan with the provided name, or a specific phase in the plan, with the provided nam, or a specific step in a
phase of the plan with the provided step name.

```bash
dcos kafka plan force-restart deploy
```

#### Force-Complete
Force complete a specific step in the provided phase. Example uses include the following: Abort a sidecar operation due
to observed failure or due to known required manual preparation that was not performed.

```bash
dcos kafka plan force-complete deploy
```
