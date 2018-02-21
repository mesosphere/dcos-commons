# Updating Configuration

You can make changes to the service after it has been launched. Configuration management is handled by the scheduler process, which in turn handles deploying {{ include.data.techName }} itself.

After making a change, the scheduler will be restarted and will automatically deploy any detected changes to the service, one node at a time. For example, a given change will first be applied to `{{ include.data.managing.podType }}-0`, then `{{ include.data.managing.podType }}-1`, and so on.

Nodes are configured with a "readiness check" to ensure that the underlying service appears to be in a healthy state before continuing with applying a given change to the next node in the sequence. However, this basic check is not foolproof and reasonable care should be taken to ensure that a given configuration change will not negatively affect the behavior of the service.

Some changes, such as decreasing the number of nodes or changing volume requirements, are not supported after initial deployment. See [Limitations](../limitations/).

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

The instructions below describe how to update the configuration for a running DC/OS service.

## Enterprise DC/OS 1.10

Enterprise DC/OS 1.10 introduces a convenient command line option that allows for easier updates to a service's configuration, as well as allowing users to inspect the status of an update, to pause and resume updates, and to restart or complete steps if necessary.

### Prerequisites

+ Enterprise DC/OS 1.10 or newer.
+ Service with a version greater than 2.0.0-x.
+ [The DC/OS CLI](https://docs.mesosphere.com/latest/cli/install/) installed and available.
+ The service's subcommand available and installed on your local machine.
  + You can install just the subcommand CLI by running `dcos package install --cli {{ include.data.packageName }}`.
  + If you are running an older version of the subcommand CLI that doesn't have the `update` command, uninstall and reinstall your CLI.
    ```bash
    $ dcos package uninstall --cli {{ include.data.packageName }}
    $ dcos package install --cli {{ include.data.packageName }}
    ```

### Preparing configuration

If you installed this service with Enterprise DC/OS 1.10, you can fetch the full configuration of a service, including any default values that were applied during installation. For example:

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} describe > options.json
```

Make any configuration changes to this `options.json` file.

If you installed this service with a prior version of DC/OS, this configuration will not have been persisted by the the DC/OS package manager. You can instead use the `options.json` file that was used when [installing the service](#initial-service-configuration).

**Note:** You must specify all configuration values in the `options.json` file when performing a configuration update. Any unspecified values will be reverted to the default values specified by the DC/OS service. See the "Recreating `options.json`" section below for information on recovering these values.

#### Recreating `options.json` (optional)

If the `options.json` from when the service was last installed or updated is not available, you will need to manually recreate it using the following steps.

First, we'll fetch the default application's environment, current application's environment, and the actual template that maps config values to the environment:

1. Ensure you have [jq](https://stedolan.github.io/jq/) installed.
1. Get the version of the package that is currently installed:
```bash
$ PACKAGE_VERSION=$(dcos package list | grep {{ include.data.packageName }} | awk '{print $2}')
```
1. Then fetch and save the environment variables that have been set for the service:
```bash
$ dcos marathon app show {{ include.data.serviceName }} | jq .env > current_env.json
```
1. To identify those values that are custom, we'll get the default environment variables for this version of the service:
```bash
$ dcos package describe --package-version=$PACKAGE_VERSION --render --app {{ include.data.serviceName }} | jq .env > default_env.json
```
1. We'll also get the entire application template:
```bash
$ dcos package describe {{ include.data.serviceName }} --app > marathon.json.mustache
```

With these files, `options.json` can be recreated.

1. Use `jq` and `diff` to compare the two:
```bash
$ diff <(jq -S . default_env.json) <(jq -S . current_env.json)
```

1. Now compare these values to the values contained in the `env` section in application template:
```bash
$ less marathon.json.mustache
```
1. Use the variable names (e.g. `{{=<% %>=}}{{service.name}}<%={{ }}=%>`) to create a new `options.json` file as described in [Initial service configuration](#initial-service-configuration).

### Starting the update

Once you are ready to begin, initiate an update using the DC/OS CLI, passing in the updated `options.json` file:

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} update start --options=options.json
```

You will receive an acknowledgement message and the DC/OS package manager will restart the Scheduler in Marathon.

See [Advanced update actions](#advanced-update-actions) for commands you can use to inspect and manipulate an update after it has started.

### Open Source DC/OS, Enterprise DC/OS 1.9 and Earlier

If you do not have Enterprise DC/OS 1.10 or later, the CLI commands above are not available. For Open Source DC/OS of any version, or Enterprise DC/OS 1.9 and earlier, you can perform changes from the DC/OS GUI.

<!-- END DUPLICATE BLOCK -->

To make configuration changes via scheduler environment updates, perform the following steps:
1. Visit `<dcos-url>` to access the DC/OS web interface.
1. Navigate to `Services` and click on the service to be configured (default `{{ include.data.serviceName }}`).
1. Click `Edit` in the upper right. On DC/OS 1.9.x, the `Edit` button is in a menu made up of three dots.
1. Navigate to `Environment` (or `Environment variables`) and search for the option to be updated.
1. Update the option value and click `Review and run` (or `Deploy changes`).
1. The Scheduler process will be restarted with the new configuration and will validate any detected changes.
1. If the detected changes pass validation, the relaunched Scheduler will deploy the changes by sequentially relaunching affected tasks as described above.

To see a full listing of available options, run `dcos package describe --config {{ include.data.packageName }}` in the CLI, or browse the {{ include.data.packageName }} package install dialog in the DC/OS web interface.

# Upgrade Software

1.  In the DC/OS web interface, destroy the `{{ include.data.serviceName }}` scheduler to be updated.

1.  Verify that you no longer see it in the DC/OS web interface.

1.  Optional: Create a JSON options file with any custom configuration, such as a non-default `DEPLOY_STRATEGY`.

```json
{
  "env": {
    "DEPLOY_STRATEGY": "parallel-canary"
  }
}
```


1.  Install the latest version of the {{ include.data.packageName }} package with the specified options:

```bash
$ dcos package install {{ include.data.packageName }} -—options=options.json
```

# Pod Info

Comprehensive information is available about every pod.  To list all pods:

```bash
dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} pod list
[
  "{{ include.data.managing.podType }}-0",
  "{{ include.data.managing.podType }}-1",
  ...
]
```

To view information about a pod, run the following command from the CLI.
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} pod info <{{ include.data.managing.podType }}-id>
```

For example:
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} pod info {{ include.data.managing.podType }}-0
{
  ... lots of JSON ...
}
```

# Pod Status
Similarly, the status for any pod may also be queried.

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} pod status <{{ include.data.managing.podType }}-id>
```

For example:

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} pod status {{ include.data.managing.podType }}-1
[
  {
    "name": "{{ include.data.managing.podType }}-1-{{ include.data.managing.taskType }}",
    "id": "{{ include.data.managing.podType }}-1-{{ include.data.managing.taskType }}__b31a70f4-73c5-4065-990c-76c0c704b8e4",
    "state": "TASK_RUNNING"
  }
]
```

# Pause a pod

Pausing a pod relaunches it in an idle command state. This allows the operator to debug the contents of the pod, possibly making changes to fix problems. While these problems are often fixed by just replacing the pod, there may be cases where an in-place repair or other operation is needed.

For example:
- A pod which crashes immediately upon starting may need additional work to be performed.
- Some services may _require_ that certain repair operations be performed manually when the task itself isn't running.
Being able to put the pod in an offline but accessible state makes it easier to resolve these situations.

After the pod has been paused, it may be started again, at which point it will be restarted and will resume running task(s) where it left off.

Here is an example session where a `{{ include.data.managing.podType }}-1` pod is crash looping due to some corrupted data in a persistent volume. The operator pauses the `{{ include.data.managing.podType }}-1` pod, then uses `task exec` to repair the pod. Following this, the operator starts the pod and it resumes normal operation:

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} debug pod pause {{ include.data.managing.podType }}-1
{
  "pod": "{{ include.data.managing.podType }}-1",
  "tasks": [
    "{{ include.data.managing.podType }}-1-{{ include.data.managing.taskType }}"
  ]
}

$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} pod status
{{ include.data.serviceName }}
└─ {{ include.data.managing.podType }}
   ├─ {{ include.data.managing.podType }}-0
   │  └─ {{ include.data.managing.podType }}-0-{{ include.data.managing.taskType }} (RUNNING)
   └─ {{ include.data.managing.podType }}-1
      └─ {{ include.data.managing.podType }}-1-{{ include.data.managing.taskType }} (PAUSING)

... repeat "pod status" until {{ include.data.managing.podType }}-1 is PAUSED ...

$ dcos task exec --interactive --tty {{ include.data.managing.podType }}-1-{{ include.data.managing.taskType }} /bin/bash
{{ include.data.managing.podType }}-1-node$ ./repair-{{ include.data.managing.podType }} && exit

$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} debug pod resume {{ include.data.managing.podType }}-1
{
  "pod": "{{ include.data.managing.podType }}-1",
  "tasks": [
    "{{ include.data.managing.podType }}-1-{{ include.data.managing.taskType }}"
  ]
}

$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} pod status
{{ include.data.serviceName }}
└─ {{ include.data.managing.podType }}
   ├─ {{ include.data.managing.podType }}-0
   │  └─ {{ include.data.managing.podType }}-0-{{ include.data.managing.taskType }} (RUNNING)
   └─ {{ include.data.managing.podType }}-1
      └─ {{ include.data.managing.podType }}-1-{{ include.data.managing.taskType }} (STARTING)

... repeat "pod status" until {{ include.data.managing.podType }}-1 is RUNNING ...
```

In the above example, all tasks in the pod were being paused and started, but it's worth noting that the commands also support pausing and starting individual tasks within a pod. For example, `dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} debug pod pause {{ include.data.managing.podType }}-1 -t {{ include.data.managing.taskType }}` will pause only the `{{ include.data.managing.taskType }}` task within the `{{ include.data.managing.podType }}-1` pod.

# Upgrading Service Version

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

The instructions below show how to safely update one version of DC/OS {{ include.data.techName }} Service to the next.

## Viewing available versions

The `update package-versions` command allows you to view the versions of a service that you can upgrade or downgrade to. These are specified by the service maintainer and depend on the semantics of the service (i.e. whether or not upgrades are reversal).

For example, run:
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} update package-versions
```

## Upgrading or downgrading a service

1. Before updating the service itself, update its CLI subcommand to the new version:
   ```bash
   $ dcos package uninstall --cli {{ include.data.packageName }}
   $ dcos package install --cli {{ include.data.packageName }} --package-version="1.1.6-5.0.7"
   ```
1. Once the CLI subcommand has been updated, call the update start command, passing in the version. For example, to update DC/OS {{ include.data.techName }} Service to version `1.1.6-5.0.7`:
   ```bash
   $ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} update start --package-version="1.1.6-5.0.7"
   ```

If you are missing mandatory configuration parameters, the `update` command will return an error. To supply missing values, you can also provide an `options.json` file (see [Updating configuration](#updating-configuration)):

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} update start --options=options.json --package-version="1.1.6-5.0.7"
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
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} update status
```

If the Scheduler is still restarting, DC/OS will not be able to route to it and this command will return an error message. Wait a short while and try again. You can also go to the Services tab of the DC/OS GUI to check the status of the restart.

## Pause

To pause an ongoing update, issue a pause command:

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} update pause
```

You will receive an error message if the plan has already completed or has been paused. Once completed, the plan will enter the `WAITING` state.

## Resume

If a plan is in a `WAITING` state, as a result of being paused or reaching a breakpoint that requires manual operator verification, you can use the `resume` command to continue the plan:

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} update resume
```

You will receive an error message if you attempt to `resume` a plan that is already in progress or has already completed.

## Force Complete

In order to manually "complete" a step (such that the Scheduler stops attempting to launch a task), you can issue a `force-complete` command. This will instruct to Scheduler to mark a specific step within a phase as complete. You need to specify both the phase and the step, for example:

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} update force-complete service-phase {{ include.data.managing.podType }}-0:[task]
```

## Force Restart

Similar to force complete, you can also force a restart. This can either be done for an entire plan, a phase, or just for a specific step.

To restart the entire plan:
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} update force-restart
```

Or for all steps in a single phase:
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} update force-restart service-phase
```

Or for a specific step within a specific phase:
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} update force-restart service-phase {{ include.data.managing.podType }}-0:[task]
```

<!-- END DUPLICATE BLOCK -->
