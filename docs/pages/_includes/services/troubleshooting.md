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

## Tasks not deploying / Resource starvation

When the scheduler is performing offer evaluation, it will log its decisions about offers it has received. This can be useful in the common case of determining why a task is failing to deploy.

In recent versions of the scheduler, a scheduler endpoint at `http://yourcluster.com/service/{{ include.service_name }}/v1/debug/offers` will display an HTML table containing a summary of recently-evaluated offers. This table's contents are currently very similar to what can be found in logs, but in a slightly more accessible format. Alternately, we can look at the scheduler's logs in `stdout` (or `stderr` in older SDK versions).

When looking at either the Offers debug endpoint, or at the scheduler logs directly, we find several examples of offers that were insufficient to deploy the remaining node. It's important to remember that _offers will regularly be rejected_ due to not meeting the needs of a deployed task and that this is _completely normal_. What we're looking for is a common theme across those rejections that would indicate what we're missing.

The following example assumes a hypothetical task requiring a pre-determined amount of resources. From scrolling through the scheduler logs, we see a couple of patterns. First, there are failures like this, where the only thing missing is CPUs. The remaining task requires 2 CPUs but this offer apparently didn't have enough:

```
INFO  2017-04-25 19:17:13,846 [pool-8-thread-1] com.mesosphere.sdk.offer.evaluate.OfferEvaluator:evaluate(69): Offer 1: failed 1 of 14 evaluation stages:
  PASS(PlacementRuleEvaluationStage): No placement rule defined
  PASS(ExecutorEvaluationStage): Offer contains the matching Executor ID
  PASS(ResourceEvaluationStage): Offer contains sufficient 'cpus': requirement=type: SCALAR scalar { value: 0.5 }
  PASS(ResourceEvaluationStage): Offer contains sufficient 'mem': requirement=type: SCALAR scalar { value: 500.0 }
  PASS(LaunchEvaluationStage): Added launch information to offer requirement
  FAIL(ResourceEvaluationStage): Failed to satisfy required resource 'cpus': name: "cpus" type: SCALAR scalar { value: 2.0 } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
  PASS(ResourceEvaluationStage): Offer contains sufficient 'mem': requirement=type: SCALAR scalar { value: 8000.0 }
  PASS(MultiEvaluationStage): All child stages passed
    PASS(PortEvaluationStage): Offer contains sufficient 'ports': requirement=type: RANGES ranges { range { begin: 9042 end: 9042 } }
    PASS(PortEvaluationStage): Offer contains sufficient 'ports': requirement=type: RANGES ranges { range { begin: 9160 end: 9160 } }
    PASS(PortEvaluationStage): Offer contains sufficient 'ports': requirement=type: RANGES ranges { range { begin: 7000 end: 7000 } }
    PASS(PortEvaluationStage): Offer contains sufficient 'ports': requirement=type: RANGES ranges { range { begin: 7001 end: 7001 } }
    PASS(PortEvaluationStage): Offer contains sufficient 'ports': requirement=type: RANGES ranges { range { begin: 8609 end: 8609 } }
    PASS(PortEvaluationStage): Offer contains sufficient 'ports': requirement=type: RANGES ranges { range { begin: 8182 end: 8182 } }
    PASS(PortEvaluationStage): Offer contains sufficient 'ports': requirement=type: RANGES ranges { range { begin: 7199 end: 7199 } }
    PASS(PortEvaluationStage): Offer contains sufficient 'ports': requirement=type: RANGES ranges { range { begin: 21621 end: 21621 } }
    PASS(PortEvaluationStage): Offer contains sufficient 'ports': requirement=type: RANGES ranges { range { begin: 8983 end: 8983 } }
    PASS(PortEvaluationStage): Offer contains sufficient 'ports': requirement=type: RANGES ranges { range { begin: 7077 end: 7077 } }
    PASS(PortEvaluationStage): Offer contains sufficient 'ports': requirement=type: RANGES ranges { range { begin: 7080 end: 7080 } }
    PASS(PortEvaluationStage): Offer contains sufficient 'ports': requirement=type: RANGES ranges { range { begin: 7081 end: 7081 } }
  PASS(VolumeEvaluationStage): Offer contains sufficient 'disk'
  PASS(VolumeEvaluationStage): Offer contains sufficient 'disk'
  PASS(VolumeEvaluationStage): Offer contains sufficient 'disk'
  PASS(VolumeEvaluationStage): Offer contains sufficient 'disk'
  PASS(LaunchEvaluationStage): Added launch information to offer requirement
  PASS(ReservationEvaluationStage): Added reservation information to offer requirement
```

If we scroll up from this rejection summary, we find a message describing what the agent had offered in terms of CPU:

```
INFO  2017-04-25 19:17:13,834 [pool-8-thread-1] com.mesosphere.sdk.offer.MesosResourcePool:consumeUnreservedMerged(239): Offered quantity of cpus is insufficient: desired type: SCALAR scalar { value: 2.0 }, offered type: SCALAR scalar { value: 0.5 }
```

Understandably, our scheduler is refusing to launch a node on a system with 0.5 remaining CPUs when the node needs 2.0 CPUs.

Another pattern we see is a message like this, where the offer is being rejected for several reasons:

```
INFO  2017-04-25 19:17:14,849 [pool-8-thread-1] com.mesosphere.sdk.offer.evaluate.OfferEvaluator:evaluate(69): Offer 1: failed 6 of 14 evaluation stages:
  PASS(PlacementRuleEvaluationStage): No placement rule defined
  PASS(ExecutorEvaluationStage): Offer contains the matching Executor ID
  FAIL(ResourceEvaluationStage): Failed to satisfy required resource 'cpus': name: "cpus" type: SCALAR scalar { value: 0.5 } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
  PASS(ResourceEvaluationStage): Offer contains sufficient 'mem': requirement=type: SCALAR scalar { value: 500.0 }
  PASS(LaunchEvaluationStage): Added launch information to offer requirement
  FAIL(ResourceEvaluationStage): Failed to satisfy required resource 'cpus': name: "cpus" type: SCALAR scalar { value: 2.0 } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } } FAIL(ResourceEvaluationStage): Failed to satisfy required resource 'mem': name: "mem" type: SCALAR scalar { value: 8000.0 } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
  FAIL(MultiEvaluationStage): Failed to pass all child stages
    FAIL(PortEvaluationStage): Failed to satisfy required resource 'ports': name: "ports" type: RANGES ranges { range { begin: 9042 end: 9042 } } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
    FAIL(PortEvaluationStage): Failed to satisfy required resource 'ports': name: "ports" type: RANGES ranges { range { begin: 9160 end: 9160 } } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
    FAIL(PortEvaluationStage): Failed to satisfy required resource 'ports': name: "ports" type: RANGES ranges { range { begin: 7000 end: 7000 } } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
    FAIL(PortEvaluationStage): Failed to satisfy required resource 'ports': name: "ports" type: RANGES ranges { range { begin: 7001 end: 7001 } } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
    FAIL(PortEvaluationStage): Failed to satisfy required resource 'ports': name: "ports" type: RANGES ranges { range { begin: 8609 end: 8609 } } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
    FAIL(PortEvaluationStage): Failed to satisfy required resource 'ports': name: "ports" type: RANGES ranges { range { begin: 8182 end: 8182 } } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
    FAIL(PortEvaluationStage): Failed to satisfy required resource 'ports': name: "ports" type: RANGES ranges { range { begin: 7199 end: 7199 } } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
    FAIL(PortEvaluationStage): Failed to satisfy required resource 'ports': name: "ports" type: RANGES ranges { range { begin: 21621 end: 21621 } } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
    FAIL(PortEvaluationStage): Failed to satisfy required resource 'ports': name: "ports" type: RANGES ranges { range { begin: 8983 end: 8983 } } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
    FAIL(PortEvaluationStage): Failed to satisfy required resource 'ports': name: "ports" type: RANGES ranges { range { begin: 7077 end: 7077 } } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
    FAIL(PortEvaluationStage): Failed to satisfy required resource 'ports': name: "ports" type: RANGES ranges { range { begin: 7080 end: 7080 } } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
    FAIL(PortEvaluationStage): Failed to satisfy required resource 'ports': name: "ports" type: RANGES ranges { range { begin: 7081 end: 7081 } } role: "{{ include.service_name }}-role" reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
  FAIL(VolumeEvaluationStage): Failed to satisfy required volume 'disk': name: "disk" type: SCALAR scalar { value: 10240.0 } role: "{{ include.service_name }}-role" disk { persistence { id: "" principal: "{{ include.service_name }}-principal" } volume { container_path: "{{ include.service_name }}-data" mode: RW } } reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
  PASS(VolumeEvaluationStage): Offer contains sufficient 'disk'
  PASS(VolumeEvaluationStage): Offer contains sufficient 'disk'
  FAIL(VolumeEvaluationStage): Failed to satisfy required volume 'disk': name: "disk" type: SCALAR scalar { value: 10240.0 } role: "{{ include.service_name }}-role" disk { persistence { id: "" principal: "{{ include.service_name }}-principal" } volume { container_path: "solr-data" mode: RW } } reservation { principal: "{{ include.service_name }}-principal" labels { labels { key: "resource_id" value: "" } } }
  PASS(LaunchEvaluationStage): Added launch information to offer requirement
  PASS(ReservationEvaluationStage): Added reservation information to offer requirement
```

In this case, we see that none of the ports our task needs are available on this system (not to mention the lack of sufficient CPU and RAM). This will typically happen when we're looking at an agent that we've already deployed to. The agent in question here is likely running an existing node for this service, where we had already reserved those ports ourselves.

We're seeing that none of the remaining agents in the cluster have room to fit our third node. To resolve this, we need to either add more agents to the DC/OS cluster or we need to reduce the requirements of our service to make it fit. In the latter case, be aware of any performance issues that may result if resource usage is reduced too far. Insufficient CPU quota will result in throttled tasks, and insufficient RAM quota will result in OOMed tasks.

This is a good example of the kind of diagnosis you can perform by skimming the scheduler logs.

## Accidentially deleted Marathon task but not service

A common user mistake is to remove the scheduler task from Marathon, which doesn't do anything to uninstall the service tasks themselves. If you do this, you have two options:

### Uninstall the rest of the service

If you really wanted to uninstall the service, you just need to complete the normal `package uninstall` steps described under [Uninstall](#uninstall).

### Recover the Scheduler

If you want to bring the scheduler back, you can do a `dcos package install` using the options that you had configured before. This will re-install a new scheduler that should match the previous one (assuming you got your options right), and it will resume where it left off. To ensure that you don't forget the options your services are configured with, we recommend keeping a copy of your service's `options.json` in source control so that you can easily recover it later. See also [Initial configuration](#initial-service-configuration).

## 'Framework has been removed'

Long story short, you forgot to run `janitor.py` the last time you ran the service. See [Uninstall](#uninstall) for steps on doing that. In case you're curious, here's what happened:

1. You ran `dcos package uninstall {{ include.package_name }} --app-id {{ include.service_name }}`. This destroyed the scheduler and its associated tasks, _but didn't clean up its reserved resources_.
1. Later on, you tried to reinstall the service. The scheduler came up and found an entry in ZooKeeper with the previous framework ID, which would have been cleaned up by `janitor.py`. The scheduler tried to re-register using that framework ID.
1. Mesos returned an error because it knows that framework ID is no longer valid. Hence the confusing 'Framework has been removed' error.

## Stuck deployments

You can sometimes get into valid situations where a deployment is being blocked by a repair operation or vice versa. For example, say you were rolling out an update to a 500 node Cassandra cluster. The deployment gets paused at node #394 because it's failing to come back, and, for whatever reason, we don't have the time or the inclination to `pod replace` it and wait for it to come back.

In this case, we can use `plan` commands to force the Scheduler to skip node #394 and proceed with the rest of the deployment:

```bash
$ dcos cassandra plan status deploy
{
  "phases": [
    {
      "id": "aefd33e3-af78-425e-ad2e-6cc4b0bc1907",
      "name": "cassandra-phase",
      "steps": [
        ...
        { "id": "f108a6a8-d41f-4c49-a1c0-4a8540876f6f", "name": "node-393:[node]", "status": "COMPLETE" },
        { "id": "83a7f8bc-f593-452a-9ceb-627d101da545", "name": "node-394:[node]", "status": "PENDING" }, # stuck here
        { "id": "61ce9d7d-b023-4a8a-9191-bfa261ace064", "name": "node-395:[node]", "status": "PENDING" },
        ...
      ],
      "status": "IN_PROGRESS"
    },
    ...
  ],
  "errors": [],
  "status": "IN_PROGRESS"
}
$ dcos plan force deploy cassandra-phase node-394:[node]
{
  "message": "Received cmd: forceComplete"
}
```

After forcing the `node-394:[node]` step, we can then see that the Plan shows it in a `COMPLETE` state, and that the Plan is proceeding with `node-395`:

```
$ dcos cassandra plan status deploy
{
  "phases": [
    {
      "id": "aefd33e3-af78-425e-ad2e-6cc4b0bc1907",
      "name": "cassandra-phase",
      "steps": [
        ...
        { "id": "f108a6a8-d41f-4c49-a1c0-4a8540876f6f", "name": "node-393:[node]", "status": "COMPLETE" },
        { "id": "83a7f8bc-f593-452a-9ceb-627d101da545", "name": "node-394:[node]", "status": "COMPLETE" },
        { "id": "61ce9d7d-b023-4a8a-9191-bfa261ace064", "name": "node-395:[node]", "status": "PENDING" },
        ...
      ],
      "status": "IN_PROGRESS"
    },
    ...
  ],
  "errors": [],
  "status": "IN_PROGRESS"
}
```

If we want to go back and fix the deployment of that node, we can simply force the scheduler to treat it as a pending operation again:

```
$ dcos plan restart deploy cassandra-phase node-394:[node]
{
  "message": "Received cmd: restart"
}
```

Now, we see that the step is again marked as `PENDING` as the Scheduler again attempts to redeploy that node:

```
$ dcos cassandra plan status deploy
{
  "phases": [
    {
      "id": "aefd33e3-af78-425e-ad2e-6cc4b0bc1907",
      "name": "cassandra-phase",
      "steps": [
        ...
        { "id": "f108a6a8-d41f-4c49-a1c0-4a8540876f6f", "name": "node-393:[node]", "status": "COMPLETE" },
        { "id": "83a7f8bc-f593-452a-9ceb-627d101da545", "name": "node-394:[node]", "status": "PENDING" },
        { "id": "61ce9d7d-b023-4a8a-9191-bfa261ace064", "name": "node-395:[node]", "status": "COMPLETE" },
        ...
      ],
      "status": "IN_PROGRESS"
    },
    ...
  ],
  "errors": [],
  "status": "IN_PROGRESS"
}
```

This example shows how steps in the deployment Plan (or any other Plan) can be manually retriggered or forced to a completed state by querying the Scheduler. This doesn't come up often, but it can be a useful tool in certain situations.

**Note:** The `dcos plan` commands will also accept UUID `id` values instead of the `name` values for the `phase` and `step` arguments. Providing UUIDs avoids the possibility of a race condition where we view the plan, then it changes structure, then we change a plan step that isn't the same one we were expecting (but which had the same name).

### Deleting a task in ZooKeeper to forcibly wipe that task

If the scheduler is still failing after `pod replace <name>` to clear a task, a last resort is to use [Exhibitor](#ZooKeeperexhibitor) to delete the offending task from the scheduler's ZooKeeper state, and then to restart the scheduler task in Marathon so that it picks up the change. After the scheduler restarts, it will do the following:
- Automatically unreserve the task's previous resources with Mesos because it doesn't recognize them anymore (via the Resource Cleanup operation described earlier).
- Automatically redeploy the task on a new agent.

**Note:** This operation can easily lead to a completely broken service. __Do this at your own risk.__ [Break glass in case of emergency](img/ops-guide-exhibitor-delete-task.png)

### OOMed task

Your tasks can be killed from an OOM if you didn't give them sufficient resources. This will manifest as sudden `Killed` messages in [Task logs](#task-logs), sometimes consistently but often not. To verify that the cause is an OOM, the following places can be checked:
- Check [Scheduler logs](#scheduler-logs) (or `dcos <svcname> pod status <podname>)` to see TaskStatus updates from mesos for a given failed pod.
- Check [Agent logs](#mesos-agent-logs) directly for mention of the Mesos Agent killing a task due to excess memory usage.

After you've been able to confirm that the problem is indeed an OOM, you can solve it by either [updating the service configuration](#updating-service-configuration) to reserve more memory, or configuring the underlying service itself to use less memory (assuming the option is available).
