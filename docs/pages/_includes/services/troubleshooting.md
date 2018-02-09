## Configuration update errors

After a configuration change, the service may enter an unhealthy state. This commonly occurs when an invalid configuration change was made by the user. Certain configuration values may not be changed, or may not be decreased. To verify whether this is the case, check the service's `deploy` plan for any errors.

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }}-dev plan show deploy
```

## Accessing Logs

Logs for the scheduler and all service nodes can be viewed from the DC/OS web interface.

- Scheduler logs are useful for determining why a node isn't being launched (this is under the purview of the Scheduler).
- Node logs are useful for examining problems in the service itself.

In all cases, logs are generally piped to files named `stdout` and/or `stderr`.

To view logs for a given node, perform the following steps:
1. Visit <dcos-url> to access the DC/OS web interface.
1. Navigate to `Services` and click on the service to be examined (default `{{ include.data.serviceName }}`).
1. In the list of tasks for the service, click on the task to be examined (scheduler is named after the service, nodes are each named e.g. `node-<#>-server` depending on their type).
1. In the task details, click on the `Logs` tab to go into the log viewer. By default, you will see `stdout`, but `stderr` is also useful. Use the pull-down in the upper right to select the file to be examined.

You can also access the logs via the Mesos UI:
1. Visit `<dcos-url>/mesos` to view the Mesos UI.
1. Click the `Frameworks` tab in the upper left to get a list of services running in the cluster.
1. Navigate into the correct framework for your needs. The scheduler runs under `marathon` with a task name matching the service name (default {{ include.data.serviceName }}). Service nodes run under a framework whose name matches the service name (default {{ include.data.serviceName }}).
1. You should now see two lists of tasks. `Active Tasks` are tasks currently running, and `Completed Tasks` are tasks that have exited. Click the `Sandbox` link for the task you wish to examine.
1. The `Sandbox` view will list files named `stdout` and `stderr`. Click the file names to view the files in the browser, or click `Download` to download them to your system for local examination. Note that very old tasks will have their Sandbox automatically deleted to limit disk space usage.

## Replacing a Permanently Failed Node

The DC/OS Elastic Service is resilient to temporary pod failures, automatically relaunching them in-place if they stop running. However, if a machine hosting a pod is permanently lost, manual intervention is required to discard the downed pod and reconstruct it on a new machine.

The following command should be used to get a list of available pods. In this example we are querying a service named `{{ include.data.serviceName }}-dev`.

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }}-dev pod list
```

The following command should then be used to replace the pod residing on the failed machine, using the appropriate `pod_name` provided in the above list.

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }}-dev pod replace <pod_name>
```

The pod recovery may then be monitored via the `recovery` plan.

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }}-dev plan show recovery
```

## Restarting a Node

If you must forcibly restart a pod's processes but do not wish to clear that pod's data, use the following command to restart the pod on the same agent machine where it currently resides. This will not result in an outage or loss of data.

The following command should be used to get a list of available pods. In this example we are querying a service named `{{ include.data.serviceName }}-dev`.

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }}-dev pod list
```

The following command should then be used to restart the pod, using the appropriate `pod_name` provided in the above list.

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }}-dev pod restart <pod_name>
```

The pod recovery may then be monitored via the `recovery` plan.

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }}-dev plan show recovery
```
