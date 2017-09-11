---
post_title: Troubleshooting
menu_order: 90
enterprise: 'no'
---

<a name="accessing-logs"></a>
# Accessing Logs

Logs for the scheduler and all service nodes can be viewed from the DC/OS web interface.

- Scheduler logs are useful for determining why a node is not being launched (this is under the purview of the Scheduler).
- Node logs are useful for examining problems in the service itself.

In all cases, logs are generally piped to files name `stdout` and/or `stderr`.

To view logs for a given node, perform the following steps:
1. Visit <dcos-url> to access the DC/OS web interface.
1. Navigate to `Services` and click on the service to be examined (default `_PKGNAME_`).
1. In the list of tasks for the service, click on the task to be examined (scheduler is named after the service, nodes are each `_NODEPOD_-<NUM>-node`).
1. In the task details, click on the `Logs` tab to go into the log viewer. By default, you will see `stdout`, but `stderr` is also useful. Use the pull-down in the upper right to select the file to be examined.

You can also access the logs via the Mesos UI:
1. Visit <dcos-url>/mesos to view the Mesos UI.
1. Click the `Frameworks` tab in the upper left to get a list of services running in the cluster.
1. Navigate into the correct framework for your needs. The scheduler runs under `marathon` with a task name matching the service name (default _`PKGNAME`_). Service nodes run under a framework whose name matches the service name (default _`PKGNAME`_).
1. You should now see two lists of tasks. `Active Tasks` are tasks currently running, and `Completed Tasks` are tasks that have exited. Click on the `Sandbox` link for the task you wish to examine.
1. The `Sandbox` view will list files named `stdout` and `stderr`. Click the file names to view the files in the browser, or click `Download` to download the file to your system for local examination. Note that very old tasks will have their Sandbox automatically deleted to limit disk space usage.

<a name="accessing-metrics"></a>
# Accessing Metrics
_PROVIDE INSTRUCTIONS FOR ACCESSING METRICS OR INDICATE A LACK OF METRICS W/I LIMITATIONS.MD._

<a name="restarting-a-node"></a>
# Restarting a Node
If you must forcibly restart a node, use the following command to restart the node on the same agent node where it currently resides. This will not result in an outage or loss of data.

```shell
dcos _PKGNAME_ --name=<service-name> pod restart <node_id>
```
<a name="replacing-a-node"></a>
# Replacing a Permanently Failed Node
The DC/OS _SERVICENAME_ Service is resilient to temporary node failures. However, if a DC/OS agent hosting a _SERVICENAME_ node is permanently lost, manual intervention is required to replace the failed node. The following command should be used to replace the node residing on the failed server.

```shell
dcos _PKGNAME_ --name=<service-name> pod replace <node_id>
```


