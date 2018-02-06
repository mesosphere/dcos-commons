---
layout: layout.pug
navigationTitle: Diagnostic Tools
title:
menuWeight: 20
excerpt:
---

DC/OS clusters provide several tools for diagnosing problems with services running in the cluster. In addition, the SDK has its own endpoints that describe what the Scheduler is doing at any given time.

## Logging

The first step to diagnosing a problem is typically to take a look at the logs. Tasks do different things, so it takes some knowledge of the problem being diagnosed to determine which task logs are relevant.

As of this writing, the best and fastest way to view and download logs is via the Mesos UI at `<dcos-url>/mesos`. On the Mesos front page you will see two lists: A list of currently running tasks, followed by a list of completed tasks (whether successful or failed).

[<img src="img/ops-guide-all-tasks.png" alt="mesos frontpage showing all tasks in the cluster" width="400"/>](img/ops-guide-all-tasks.png)

The `Sandbox` link for one of these tasks shows a list of files from within the task itself. For example, here's a sandbox view of a `broker-2` task from the above list:

[<img src="img/ops-guide-task-sandbox.png" alt="contents of a task sandbox" width="400"/>](img/ops-guide-task-sandbox.png)

If the task is based on a Docker image, this list will only show the contents of `/mnt/sandbox`, and not the rest of the filesystem. If you need to view filesystem contents outside of this directory, you will need to use `dcos task exec` or `nsenter` as described below under [Running commands within containers](#running-commands-within-containers).

In the above task list there are multiple services installed, resulting in a pretty large list. The list can be filtered using the text box at the upper right, but there may be duplicate names across services. For example there are two instances of `confluent-kafka` and they're each running a `broker-0`. As the cluster grows, this confusion gets proportionally worse. We want to limit the task list to only the tasks that are relevant to the service being diagnosed. To do this, click "Frameworks" on the upper left to see a list of all the installed frameworks (mapping to our services):

[<img src="img/ops-guide-frameworks-list.png" alt="listing of frameworks installed to the cluster" width="400"/>](img/ops-guide-frameworks-list.png)

We then need to decide which framework to select from this list. This depends on what task we want to view:

### Scheduler logs

If the issue is one of deployment or management, e.g. a service is 'stuck' in initial deployment, or a task that previously went down isn't being brought back at all, then the Scheduler logs will likely be the place to find out why.

From Mesos's perspective, the Scheduler is being run as a Marathon app. Therefore we should pick `marathon` from this list and then find our Scheduler in the list of tasks.

[<img src="img/ops-guide-framework-tasks-marathon.png" alt="listing of tasks running in the marathon framework (Marathon apps)" width="400"/>](img/ops-guide-framework-tasks-marathon.png)

Scheduler logs can be found either via the main Mesos frontpage in small clusters (possibly using the filter box at the top right), or by navigating into the list of tasks registered against the `marathon` framework in large clusters. In SDK services, the Scheduler is typically given the same name as the service. For example a `kafka-dev` service's Scheduler would be named `kafka-dev`. We click the `Sandbox` link to view the Sandbox portion of the Scheduler filesystem, which contains files named `stdout` and `stderr`. These files respectively receive the stdout/stderr output of the Scheduler process, and can be examined to see what the Scheduler is doing.

[<img src="img/ops-guide-scheduler-sandbox.png" alt="contents of a scheduler sandbox" width="400"/>](img/ops-guide-scheduler-sandbox.png)

For a good example of the kind of diagnosis you can perform using SDK Scheduler logs, see the below use case of [Tasks not deploying / Resource starvation](#tasks-not-deploying--resource-starvation).

### Task logs

When the issue being diagnosed has to do with the service tasks, e.g. a given task is crash looping, the task logs will likely provide more information. The tasks being run as a part of a service are registered against a framework matching the service name. Therefore, we should pick `<service-name>` from this list to view a list of tasks specific to that service.

[<img src="img/ops-guide-framework-tasks-service.png" alt="listing of tasks running in a framework (Service tasks)" width="400"/>](img/ops-guide-framework-tasks-service.png)

In the above list, we see separate lists of Active and Completed tasks:
- Active tasks are still running. These give a picture of the current activity of the service.
- Completed tasks have exited for some reason, whether successfully or due to a failure. These give a picture of recent activity of the service. **Note:** Older completed tasks will be automatically garbage collected and their data may no longer be available here.

Either or both of these lists may be useful depending on the context. Click on the `Sandbox` link for one of these tasks and then start looking at sandbox content. Files named `stderr` and `stdout` hold logs produced both by the SDK Executor process (a small wrapper around the service task) as well as any logs produced by the task itself. These files are automatically paginated at 2MB increments, so older logs may also be examined until they are automatically pruned. For an example of this behavior, see the [scheduler sandbox](img/ops-guide-scheduler-sandbox.png) linked earlier.

[<img src="img/ops-guide-task-sandbox.png" alt="contents of a task sandbox" width="400"/>](img/ops-guide-task-sandbox.png)

### Mesos Agent logs

Occasionally, it can also be useful to examine what a given Mesos agent is doing. The Mesos Agent handles deployment of Mesos tasks to a given physical system in the cluster. One Mesos Agent runs on each system. These logs can be useful for determining if there's a problem at the system level that is causing alerts across multiple services on that system.

Navigate to the agent you want to view either directly from a task by clicking the "Agent" item in the breadcrumb when viewing a task (this will go directly to the agent hosting the task), or by navigating through the "Agents" menu item at the top of the screen (you will need to select the desired agent from the list).

In the Agent view, you'll see a list of frameworks with a presence on that Agent. In the left pane you'll see a plain link named "LOG". Click that link to view the agent logs.

[<img src="img/ops-guide-agent.png" alt="view of tasks running on a given agent" width="400"/>](img/ops-guide-agent.png)

### Logs via the CLI

You can also access logs via the [DC/OS CLI](https://dcos.io/docs/latest/usage/cli/install/) using the `dcos task log` command. For example, lets assume the following list of tasks in a cluster:

```bash
$ dcos task
NAME                  HOST        USER  STATE  ID
broker-0              10.0.0.242  root    R    broker-0__81f56cc1-7b3d-4003-8c21-a9cd45ea6a21
broker-0              10.0.3.27   root    R    broker-0__75bcf7fd-7831-4f70-9cb8-9cb6693f4237
broker-1              10.0.0.242  root    R    broker-1__6bf127ab-5edc-4888-9dd3-f00be92b291c
broker-1              10.0.1.188  root    R    broker-1__d799afdb-78bf-44e9-bb63-d16cfc797d00
broker-2              10.0.1.151  root    R    broker-2__4a293161-b89f-429e-a22e-57a1846eb271
broker-2              10.0.3.60   root    R    broker-2__76f45cb0-4db9-41ad-835c-2cedf3d7f725
[...]
```

In this case we have two overlapping sets of brokers from two different Kafka installs. Given these tasks, we can do several different things by combining task filters, file selection, and the `--follow` argument:

```bash
$ dcos task log broker                # get recent stdout logs from all six 'broker-#' instances
$ dcos task log broker-0              # get recent stdout logs from two 'broker-0' instances
$ dcos task log broker-0__75          # get recent stdout logs from the 'broker-0' instance on 10.0.3.27
$ dcos task log --follow broker-0__75 # 'tail -f' the stdout logs from that broker instance
$ dcos task log broker-0__75 stderr   # get recent stderr logs from that broker instance
```

## Metrics

### DC/OS >= 1.11
The scheduler's metrics are reported via three different mechanisms: `JSON`, [prometheus](https://prometheus.io/) and [StatsD](https://github.com/etsy/statsd). The StatsD metrics are pushed to the address defined by the environment variables `STATSD_UDP_HOST` and `STATSD_UDP_PORT`. See [DC/OS Metrics documentation](https://dcos.io/docs/1.10/metrics/) for more details.

The JSON representation of the metrics is available at the `/v1/metrics` endpoint`.

####### JSON
```json
{
	"version": "3.1.3",
	"gauges": {},
	"counters": {
		"declines.long": {
			"count": 15
		},
		"offers.processed": {
			"count": 18
		},
		"offers.received": {
			"count": 18
		},
		"operation.create": {
			"count": 5
		},
		"operation.launch_group": {
			"count": 3
		},
		"operation.reserve": {
			"count": 20
		},
		"revives": {
			"count": 3
		},
		"task_status.task_running": {
			"count": 6
		}
	},
	"histograms": {},
	"meters": {},
	"timers": {
		"offers.process": {
			"count": 10,
			"max": 0.684745927,
			"mean": 0.15145255818999337,
			"min": 5.367950000000001E-4,
			"p50": 0.0035879090000000002,
			"p75": 0.40317217800000005,
			"p95": 0.684745927,
			"p98": 0.684745927,
			"p99": 0.684745927,
			"p999": 0.684745927,
			"stddev": 0.24017017290826104,
			"m15_rate": 0.5944843686231079,
			"m1_rate": 0.5250565015924039,
			"m5_rate": 0.583689104996544,
			"mean_rate": 0.3809369986002824,
			"duration_units": "seconds",
			"rate_units": "calls/second"
		}
	}
}
```

The Prometheus representation of the metrics is available at the `/v1/metrics/prometheus` endpoint.
####### Prometheus
```
# HELP declines_long Generated from Dropwizard metric import (metric=declines.long, type=com.codahale.metrics.Counter)
# TYPE declines_long gauge
declines_long 20.0
# HELP offers_processed Generated from Dropwizard metric import (metric=offers.processed, type=com.codahale.metrics.Counter)
# TYPE offers_processed gauge
offers_processed 24.0
# HELP offers_received Generated from Dropwizard metric import (metric=offers.received, type=com.codahale.metrics.Counter)
# TYPE offers_received gauge
offers_received 24.0
# HELP operation_create Generated from Dropwizard metric import (metric=operation.create, type=com.codahale.metrics.Counter)
# TYPE operation_create gauge
operation_create 5.0
# HELP operation_launch_group Generated from Dropwizard metric import (metric=operation.launch_group, type=com.codahale.metrics.Counter)
# TYPE operation_launch_group gauge
operation_launch_group 4.0
# HELP operation_reserve Generated from Dropwizard metric import (metric=operation.reserve, type=com.codahale.metrics.Counter)
# TYPE operation_reserve gauge
operation_reserve 20.0
# HELP revives Generated from Dropwizard metric import (metric=revives, type=com.codahale.metrics.Counter)
# TYPE revives gauge
revives 4.0
# HELP task_status_task_finished Generated from Dropwizard metric import (metric=task_status.task_finished, type=com.codahale.metrics.Counter)
# TYPE task_status_task_finished gauge
task_status_task_finished 1.0
# HELP task_status_task_running Generated from Dropwizard metric import (metric=task_status.task_running, type=com.codahale.metrics.Counter)
# TYPE task_status_task_running gauge
task_status_task_running 8.0
# HELP offers_process Generated from Dropwizard metric import (metric=offers.process, type=com.codahale.metrics.Timer)
# TYPE offers_process summary
offers_process{quantile="0.5",} 2.0609500000000002E-4
offers_process{quantile="0.75",} 2.2853200000000001E-4
offers_process{quantile="0.95",} 0.005792643
offers_process{quantile="0.98",} 0.005792643
offers_process{quantile="0.99",} 0.111950848
offers_process{quantile="0.999",} 0.396119612
offers_process_count 244.0
```

## Running commands within containers

An extremely useful tool for diagnosing task state is the ability to run arbitrary commands _within_ the task. The available tools for doing this depend on the version of DC/OS you're using:

### DC/OS >= 1.9

DC/OS 1.9 introduced the `task exec` command as a convenient frontend to `nsenter`, which is described below.

#### Prerequisites

- SSH keys for accessing your cluster configured (i.e. via `ssh-add`). SSH is used behind the scenes to get into the cluster.

- A [recent version of the DC/OS CLI](https://dcos.io/docs/latest/usage/cli/install/) with support for the `task exec` command.

#### Using `dcos task exec`

Once you're set up, running commands is very straightforward. For example, let's assume the list of tasks from the CLI logs section above, where there's two `broker-0` tasks, one named `broker-0__81f56cc1-7b3d-4003-8c21-a9cd45ea6a21` and another named `broker-0__75bcf7fd-7831-4f70-9cb8-9cb6693f4237`. Unlike with `task logs`, we can only run `task exec` on one command at a time, so if two tasks match the task filter then we see the following error:

```bash
$ dcos task exec broker-0 echo hello world
There are multiple tasks with ID matching [broker-0]. Please choose one:
	broker-0__81f56cc1-7b3d-4003-8c21-a9cd45ea6a21
	broker-0__75bcf7fd-7831-4f70-9cb8-9cb6693f4237
```

Therefore we need to be more specific:

```bash
$ dcos task exec broker-0__75 echo hello world
hello world
$ dcos task exec broker-0__75 pwd
/
```

We can also run interactive commands using the `-it` flags (short for `--interactive --tty`):

```bash
$ dcos task exec --interactive --tty broker-0__75 /bin/bash
broker-container# echo hello world
hello world
broker-container# pwd
/
broker-container# exit
```

While you could technically change the container filesystem using `dcos task exec`, any changes will be destroyed if the container restarts.

### DC/OS <= 1.8

DC/OS 1.8 and earlier do not support `dcos task exec`, but `dcos node ssh` and `nsenter` may be used instead to get the same thing, with a little more effort.

First, run `dcos task` to get the list of tasks (and their respective IPs), and cross-reference that with `dcos node` to get the list of agents (and their respective IPs). For example:

```bash
$ dcos task
NAME                  HOST        USER  STATE  ID
broker-0              10.0.1.151  root    R    broker-0__81f56cc1-7b3d-4003-8c21-a9cd45ea6a21
broker-0              10.0.3.27   root    R    broker-0__75bcf7fd-7831-4f70-9cb8-9cb6693f4237
$ dcos node
 HOSTNAME       IP                         ID
10.0.0.242  10.0.0.242  2fb7eb4d-d4fc-44b8-ab44-c858f2233675-S0
10.0.1.151  10.0.1.151  2fb7eb4d-d4fc-44b8-ab44-c858f2233675-S1
10.0.1.188  10.0.1.188  2fb7eb4d-d4fc-44b8-ab44-c858f2233675-S2
...
```

In this case we're interested in the `broker-0` on `10.0.1.151`. We can see that `broker-0`'s Mesos Agent has an ID of `2fb7eb4d-d4fc-44b8-ab44-c858f2233675-S1`. Let's SSH into that machine using `dcos node ssh`:

```bash
$ dcos node ssh --master-proxy --mesos-id=2fb7eb4d-d4fc-44b8-ab44-c858f2233675-S1
agent-system$
```

Now that we're logged into the host Agent machine, we need to find a relevant PID for the `broker-0` container. This can take some guesswork:

```bash
agent-system$ ps aux | grep -i confluent
...
root      5772  0.6  3.3 6204460 520280 ?      Sl   Apr25   2:34 /var/lib/mesos/slave/slaves/2fb7eb4d-d4fc-44b8-ab44-c858f2233675-S0/frameworks/2fb7eb4d-d4fc-44b8-ab44-c858f2233675-0004/executors/broker-0__1eb65420-535e-477b-9ac1-797e79c15277/runs/f5377eac-3a87-4080-8b80-128434e42a25/jre1.8.0_121//bin/java ... kafka_confluent-3.2.0/config/server.properties
root      6059  0.7 10.3 6203432 1601008 ?     Sl   Apr25   2:43 /var/lib/mesos/slave/slaves/2fb7eb4d-d4fc-44b8-ab44-c858f2233675-S0/frameworks/2fb7eb4d-d4fc-44b8-ab44-c858f2233675-0003/executors/broker-1__8de30046-1016-4634-b43e-45fe7ede0817/runs/19982072-08c3-4be6-9af9-efcd3cc420d3/jre1.8.0_121//bin/java ... kafka_confluent-3.2.0/config/server.properties
...
```

As we can see above, there appear to be two likely candidates, one on PID 5772 and the other on PID 6059. The one on PID 5772 has mention of `broker-0` so that's probably the one we want. Lets run the `nsenter` command using PID 6059:

```bash
agent-system$ sudo nsenter --mount --uts --ipc --net --pid --target 6059
broker-container#
```

Looks like we were successful! Now we can run commands inside this container to verify that it's the one we really want, and then proceed with the diagnosis.

## Querying the Scheduler

The Scheduler exposes several HTTP endpoints that provide information on any current deployment as well as the Scheduler's view of its tasks. For a full listing of HTTP endpoints, see the [API reference](http://mesosphere.github.io/dcos-commons/reference/swagger-api/). The Scheduler endpoints most useful to field diagnosis come from three sections:

- __Plan__: Describes any work that the Scheduler is currently doing, and what work it's about to do. These endpoints also allow manually triggering Plan operations, or restarting them if they're stuck.
- __Pods__: Describes the tasks that the Scheduler has currently deployed. The full task info describing the task environment can be retrieved, as well as the last task status received from Mesos.
- __State__: Access to other miscellaneous state information such as service-specific properties data.

For full documentation of each command, see the [API Reference](https://mesosphere.github.io/dcos-commons/reference/swagger-api/). Here is an example of invoking one of these commands against a service named `hello-world` via `curl`:

```bash
$ export AUTH_TOKEN=$(dcos config show core.dcos_acs_token)
$ curl -k -H "Authorization: token=$AUTH_TOKEN" https://<dcos_url>/service/hello-world/v1/plans/deploy
```

These endpoints may also be conveniently accessed using the SDK CLI after installing a service. See `dcos <svcname> -h` for a list of all commands. These are wrappers around the above API.

For example, let's get a list of pods using the CLI, and then via the HTTP API:

```bash
$ dcos datastax-dse --name=mydse pod list
[
  "dse-0",
  "dse-1",
  "dse-2",
  "opscenter-0",
  "studio-0"
]
$ curl -k -H "Authorization: token=$(dcos config show core.dcos_acs_token)" <dcos-url>/service/dse/v1/pod
[
  "dse-0",
  "dse-1",
  "dse-2",
  "opscenter-0",
  "studio-0"
]
```

The `-v` (or `--verbose`) argument allows you to view and diagnose the underlying requests made by the CLI:

```bash
$ dcos datastax-dse --name=mydse -v pod list
2017/04/25 15:03:43 Running DC/OS CLI command: dcos config show core.dcos_url
2017/04/25 15:03:44 HTTP Query: GET https://yourcluster.com/service/dse/v1/pod
2017/04/25 15:03:44 Running DC/OS CLI command: dcos config show core.dcos_acs_token
2017/04/25 15:03:44 Running DC/OS CLI command: dcos config show core.ssl_verify
[
  "dse-0",
  "dse-1",
  "dse-2",
  "opscenter-0",
  "studio-0"
]
2017/04/25 15:03:44 Response: 200 OK (-1 bytes)
```

## ZooKeeper/Exhibitor

**Break glass in case of emergency: This should only be used as a last resort. Modifying anything in ZooKeeper directly may cause your service to behave in inconsistent, even incomprehensible ways.**

DC/OS comes with Exhibitor, a commonly used frontend for viewing ZooKeeper. Exhibitor may be accessed at `<dcos-url>/exhibitor`. A given SDK service will have a node named `dcos-service-<svcname>` visible here. This is where the Scheduler puts its state, so that it isn't lost if the Scheduler is restarted. In practice it's far easier to access this information via the Scheduler API (or via the service CLI) as described earlier, but direct access using Exhibitor can be useful in situations where the Scheduler itself is unavailable or otherwise unable to serve requests.

[<img src="img/ops-guide-exhibitor-view-taskstatus.png" alt="viewing a task's most recent TaskStatus protobuf in Exhibitor" width="400"/>](img/ops-guide-exhibitor-view-taskstatus.png)
