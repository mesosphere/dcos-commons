---
layout: gh-basic
title: Tutorial
---

# Overview

Mesos is a powerful resource allocator, but writing a production
stateful service can be complex, tedious, and error-prone.  The DC/OS
Stateful Service SDK provides a high-level interface for
writing stateful services.  Now, you can write a stateful service
complete with
persistent volumes, fault tolerance, and configuration management in
about 100 lines of code.  This SDK is the product of Mesosphere's
experience writing production stateful services such as [DC/OS Apache
Kafka](https://docs.mesosphere.com/latest/usage/service-guides/kafka/)
, [DC/OS Apache
Cassandra](https://docs.mesosphere.com/latest/usage/service-guides/
cassandra/), and [DC/OS Apache
HDFS](https://docs.mesosphere.com/latest/usage/service-guides/hdfs/).

Read about the [features](#Features-at-a-glance) to learn more about
the DC/OS Stateful Service SDK.

# Introduction

In this tutorial, we'll build an example
service called `data-store`.  It is composed of a small number of
`meta-data` nodes and a larger number of `data` nodes.  This structure
is meant to model a service with a small number of coordinating master
nodes and a large number of dependent agent nodes.  Each task runs a
command that writes some data to a file, then sleeps.

The goal state for this fictional `data-store` is to keep two
`meta-data` and N `data` nodes running, where, initially, N = 3.
`meta-data` nodes must come up before `data` nodes.  The service
scales by starting more `data` nodes.

This tutorial includes code snippets. The final
source code is available at
[http://github.com/mesosphere/sdk-reference-framework](http://github.
com/mesosphere/sdk-reference-framework).

## Architecture

![DC/OS Service Architecture](architecture.png)

A DC/OS service is composed of **schedulers** and **executors**.  The
scheduler is run by Marathon, which supervises the scheduler,
restarting it upon failure.  The scheduler, in turn, starts and
supervises the executors.  If specified in the `ServiceSpecification`,
the
executors will contain a **persistent volume**, which is a folder or
mount point on the agent that persists even after a task fails. This
allows a stateful service to recover with its data intactafter task
failure or temporary node failure.

In this tutorial, `data-store` is the scheduler, and `meta-data-{0,1}`
and `data-{0-1}` are the executors.

## Requirements
- JDK 8
- Gradle 3.2 (recommended)
- AWS account with S3 upload permissions
- [`aws` CLI](https://aws.amazon.com/cli/)

# Create a Project

The Java library for The DC/OS Stateful Services SDK is
`dcos-commons`.  Java projects using `dcos-commons` can use any Java
build system, but for this tutorial, we'll assume you're using Gradle.

First, we create our `build.gradle` file:

```
group 'com.mesosphere.sdk'
version '1.0-SNAPSHOT'

apply plugin: 'java'
apply plugin: 'application'

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url "http://downloads.mesosphere.com/maven/"
    }
}

dependencies {
    compile "mesosphere:dcos-commons:0.7.10-SNAPSHOT"
    compile "org.slf4j:slf4j-simple:1.7.21"
}

task wrapper(type: Wrapper) {
    gradleVersion = '3.2'
}

mainClassName = 'com.mesosphere.sdk.reference.scheduler.Main'
```

# Define a Service

## Create a Service Specification

The first step to running a service is to create a
`ServiceSpecification`, which defines the behavior of the service.

A `ServiceSpecification` is mostly a list of `TaskSet`s.
The tasks defined in this list are launched in order.  We can use this
ordering to define tiers of services.  In this example, we want all
`meta-data` nodes to come up before any `data` node:

```java
private static final String TASK_METADATA_NAME = "meta-data";
private static final int TASK_METADATA_COUNT = Integer.valueOf(System.getenv("METADATA_COUNT"));
private static final double TASK_METADATA_CPU = Double.valueOf(System.getenv("METADATA_CPU"));
private static final double TASK_METADATA_MEM_MB = Double.valueOf(System.getenv("METADATA_MEM"));
private static final double TASK_METADATA_DISK_MB = Double.valueOf(System.getenv("METADATA_DISK"));
private static final String TASK_METADATA_URI = System.getenv("METADATA_URI");
private static final String TASK_METADATA_CMD = "bash meta-data";

private static final String TASK_DATA_NAME = "data";
private static final int TASK_DATA_COUNT = Integer.valueOf(System.getenv("DATA_COUNT"));
private static final double TASK_DATA_CPU = Double.valueOf(System.getenv("DATA_CPU"));
private static final double TASK_DATA_MEM_MB = Double.valueOf(System.getenv("DATA_MEM"));
private static final double TASK_DATA_DISK_MB = Double.valueOf(System.getenv("DATA_DISK"));
private static final String TASK_DATA_URI = System.getenv("DATA_URI");
private static final String TASK_DATA_CMD = "bash data";

private static ServiceSpecification getServiceSpecification() {
    return new ServiceSpecification() {
        @Override
        public String getName() {
            return SERVICE_NAME;
        }

        @Override
        public List<TaskSet> getTaskSets() {
            return Arrays.asList(
                    DefaultTaskSet.create(
                            TASK_METADATA_COUNT,
                            TASK_METADATA_NAME,
                            getCommand(TASK_METADATA_CMD, TASK_METADATA_URI),
                            getResources(TASK_METADATA_CPU, TASK_METADATA_MEM_MB),
                            getVolumes(TASK_METADATA_DISK_MB, TASK_METADATA_NAME),
                            Optional.of(TaskTypeGenerator.createAvoid(TASK_METADATA_NAME)),
                            Optional.of(getHealthCheck(TASK_METADATA_NAME))),
                    DefaultTaskSet.create(
                            TASK_DATA_COUNT,
                            TASK_DATA_NAME,
                            getCommand(TASK_DATA_CMD, TASK_DATA_URI),
                            getResources(TASK_DATA_CPU, TASK_DATA_MEM_MB),
                            getVolumes(TASK_DATA_DISK_MB, TASK_DATA_NAME),
                            Optional.of(TaskTypeGenerator.createAvoid(TASK_DATA_NAME)),
                            Optional.of(getHealthCheck(TASK_DATA_NAME))));
        }
    };
}
```

A `TaskSet` contains the bulk of the definition.  It
contains the command to run, the resources to run with, and the number
of tasks.  As you can see, it is common for DC/OS services to read
their config from environment variables.

At a minimum, the command must contain a `value`, which is the shell
command to invoke.  It will likely also contain one or more `URIs`
pointing to resources to download before execution.  In this example,
we're downloading the `metadata` and `data` binaries from an HTTP
server:

```java
private static Protos.CommandInfo getCommand(String cmd, String uri) {
    return Protos.CommandInfo.newBuilder()
              .addUris(Protos.CommandInfo.URI.newBuilder().setValue(uri))
              .setValue(cmd)
              .build();
}
```

Resurces are specified via `ResourceSpecification`s.  Tasks must run
with at least `cpus` and `mem` resources, though we may specify any
Mesos resource, including `ports`:

```java
private static Collection<ResourceSpecification> getResources(double cpu, double memMb) {
    return Arrays.asList(
            new DefaultResourceSpecification(
                    "cpus",
                    ValueUtils.getValue(ResourceUtils.getUnreservedScalar("cpus", cpu)),
                    ROLE,
                    PRINCIPAL),
            new DefaultResourceSpecification(
                    "mem",
                    ValueUtils.getValue(ResourceUtils.getUnreservedScalar("mem", memMb)),
                    ROLE,
                    PRINCIPAL));
}
```

In Mesos, `disk` is just another resource, like `cpus` and `mem`.
However, stateful services must manage `disk` differently.  Rather
than simply accepting the resource like `cpus` or `mem`, the scheduler
must first create a
[volume](http://mesos.apache.org/documentation/latest/persistent-
volume/)
from the disk resources so that it persists beyond the lifetime of the
task.  The `VolumeSpecification` is a high-level interface that
handles this for us:

```java
private static Collection<VolumeSpecification> getVolumes(double diskMb, String taskName) {
    VolumeSpecification volumeSpecification = new DefaultVolumeSpecification(
            diskMb,
            VolumeSpecification.Type.ROOT,
            taskName + CONTAINER_PATH_SUFFIX,
            ROLE,
            PRINCIPAL);

    return Arrays.asList(volumeSpecification);
}
```

Finally, we can attach a health check to our tasks.  If the check
fails, the Mesos agent will kill the task, and the scheduler will
restart it.

```java
private static Protos.HealthCheck getHealthCheck(String name) {
    Protos.CommandInfo commandInfo = Protos.CommandInfo.newBuilder()
        .setValue("stat %s%s/output".format(name, CONTAINER_PATH_SUFFIX))
        .build()

    return Protos.HealthCheck.newBuilder()
        .setCommand(commandInfo)
        .build();
}
```

## Define a Main Class

Now that we have a `ServiceSpecification`, we must define a Main class
that runs it.  To do this, create a `Service` object, and register the
`ServiceSpecification` with it:

```java
public class Main {
    private static final int API_PORT = Integer.valueOf(System.getenv("PORT0"));
    public static void main(String[] args) throws Exception {
        new DefaultService(API_PORT).register(getServiceSpecification());
    }
}
```

`DefaultService` takes a single parameter, `apiPort`. `apiPort`
specifies which
port its HTTP server will bind to.  Upon registering the
`ServiceSpecification`, our service will start its HTTP server,
register as a Mesos framework, and start launching tasks.

# Deployment

Now that we've defined our service, let's build and install it.  The
first thing we'll need is a DC/OS cluster.

## Start cluster

Go to [https://dcos.io/install/](https://dcos.io/install/) to install
a DC/OS cluster.

## Install the DC/OS CLI

If you have not already installed the CLI, do so by following [these
instructions](https://dcos.io/docs/latest/usage/cli/install/).

## Install

Now that we have our cluster and CLI, we can install our service.
DC/OS services are deployed to users via DC/OS packages.  The first
step is to define our package.

### Define a DC/OS Package

A DC/OS package is a set of four
files.  You can learn more about the package format
[here](http://github.com/mesosphere/universe), but, in summary, it
looks like this:

- **package.json**: Package metadata
- **marathon.json.mustache**: Marathon app definition.  The template
is rendered with the values in config.json, resource.json,
and user-provided config.
- **config.json**: JSON Schema defining the configuration for the app.
- **resource.json**: External resources.

The majority of the package definition resides in
**marathon.json.mustache**.  See the
[reference
framework](https://github.com/mesosphere/sdk-reference-framework/tree/
master/universe)
for an example.

Let's place these files in a directory in our project called
`universe/`.

Now we build our project:

```bash
$ ./gradlew distZip
```

### Build a DC/OS Package

Then we must build our DC/OS package.  DC/OS packages are stored in
*repos*.  The most common repo is the
[*Mesosphere Universe*](https://github.com/mesosphere/universe), which
is public. We would publish our package there if we wished to
share it with the world.  For now, we just want to create a simple
repo containing our single package.

**NOTE:** The current workflow of creating a package *and* a repo is
  cumbersome.  We're sorry about this.  DC/OS 1.10, which is slated to
  be released in December 2016, will introduce the ability to install
  a package directly, rather than forcing you to wrap it in a repo.

```bash
~ $ cd /path/to/dcos-commons
dcos-commons $ ./tools/publish_aws.py \
      data-store \
      universe/ \
      reference-scheduler/build/distributions/scheduler.zip
```

`./publish_aws.py` uploads the artifact to S3, rewrites the package to
include links to the artifact, creates a repo containing that package,
and uploads that repo to S3.  The end result is a URL for a DC/OS
repo.

We now add the repo URL printed by `publish_aws.py` to our cluster:

```bash
$ dcos package repo add --index=0 dev-repo <repo_url>
```

Then, install our package:

```bash
$ dcos package install data-store
```

Visit **Services** > **data-store** in the DC/OS UI to watch
`data-store` deploying.

To verify that `data-store` has completed deploying, make the
following HTTP request from the DC/OS CLI. To learn how to make
requests to the HTTP API,
please read the [HTTP API](#http-api) section:

```
GET /v1/plan/status
```

This HTTP request will return JSON to indicate the status of the
service. If the deployment is complete, `plan.status` will equal
"COMPLETE".

When deployment is complete, two `meta-data`
tasks and three `data` tasks will be visible in the DC/OS Services UI.

## Uninstall

To do a complete uninstall, we first uninstall our service using the
DC/OS CLI.  This stops and tears down the framework.

```bash
$ dcos package uninstall data-store
```

However, uninstalling the package does not remove persistent state
like Zookeeper state and persistent volumes. Mesosphere provides a
Docker image to remove persistent state:

```bash
local $ AUTH_TOKEN=$(dcos config show core.dcos_acs_token)
local $ dcos node ssh --master-proxy --leader
cluster $ docker run mesosphere/janitor /janitor.py \
    -r data-store-role \
    -p data-store-principal \
    -z dcos-service-data-store \
    --auth_token=$AUTH_TOKEN
```

For more information on the janitor image, see
https://github.com/mesosphere/framework-cleaner.


# Plan management
As noted earlier, stateful services often have particular deployment
requirements.  These deployments may involve the initial creation of
Tasks or a change in Task configuration or the scaling of a
particular kind of task or an upgrade to a new version of a Task.  Each
of these scenarios comes with attendant risks and benefits.  In some
cases (as in initial install) you may be happy to have a service rollout
without intervention.  In others, as in a configuration update, one may
wish to deploy some portion of a service and check stability before
completing a deployment.

In order to provide the necessary flexibility and control necessary to
achieve these outcomes, the SDK organizes deployment of tasks by means
of Plans. Plans are composed of Phases which are composed of Blocks.
A Block encapsulates the smallest element of work executed in a Plan and
is the plan element which encapsulates the launching of Mesos Tasks.

Phases are groups of Blocks which have some semantic relationship.  At
least two plans are generated automatically by the SDK when presented
with a `ServiceSpecification`.  These are the deployment and recovery
plans.  The deployment plan is concerned with deploying Tasks.  It
deploys Tasks for the first time, and when configuration updates are
indicated by a user.  The recovery plan is concerned with defining the
necessary operations which must be performed when transient or permanent
failures are encountered.

Let us examine the deployment plan of the example data-store service
after its initial installation has completed.

Executing the following command:

```
GET /v1/plan"
```

generates the following output:

```json
{
	"phases": [{
		"id": "31a3ca99-bdbd-45dd-a8f0-cc93feade61e",
		"name": "meta-data",
		"blocks": [{
			"id": "2a0db529-095b-4b63-8c56-075aa442c898",
			"status": "COMPLETE",
			"name": "meta-data-0",
			"message": "Block: meta-data-0",
			"has_decision_point": false
		}, {
			"id": "99210582-cdfb-4b63-95e9-19600f117dc7",
			"status": "COMPLETE",
			"name": "meta-data-1",
			"message": "Block: meta-data-1",
			"has_decision_point": false
		}],
		"status": "COMPLETE"
	}, {
		"id": "927ac279-b969-4106-9089-40264ae15dc4",
		"name": "data",
		"blocks": [{
			"id": "efcf9dc0-488f-4f9e-ae0c-1ac16c401e0a",
			"status": "COMPLETE",
			"name": "data-0",
			"message": "Block: data-0",
			"has_decision_point": false
		}, {
			"id": "a0e0905b-d4da-4cec-84b9-461a60a92818",
			"status": "COMPLETE",
			"name": "data-1",
			"message": "Block: data-1",
			"has_decision_point": false
		}, {
			"id": "fa85a843-e6eb-4074-b353-875197a18adb",
			"status": "COMPLETE",
			"name": "data-2",
			"message": "Block: data-2",
			"has_decision_point": false
		}],
		"status": "COMPLETE"
	}],
	"errors": [],
	"status": "COMPLETE"
}
```

The first thing to notice is that the `TaskSet`s defined in a
`ServiceSpecification` are mapped to deployment Phases. The data-store
example service has `TaskSet`s encapsulating `meta-data` and `data`
Tasks in that order.  The deployment plan above has the respective
`meta-data` and `data` Phases.

In its default configuration, the two `meta-data` Tasks and three `data`
Tasks are launched.  We can see that a Block has been created
encapsulating each of those tasks.  The names of Blocks map to the names
of the `TaskSpecification` objects within a `TaskSet`.  The status of a
Block can be in one of 3 states.  It may be `PENDING`, `IN_PROGRESS`, or
`COMPLETE`.

By default all Phases and the Blocks within them are rolled out
serially.  Each Block must reach a `COMPLETE` state before the
next Block may be started.  Beyond this automatic behavior, it is often
desirable to manually pause/resume a deployment.  For example, in a
production setting multiple uncoordinated deployments may cause
unexpected effects, including causing unavailability or severely
degraded performance.  In thes cases and others, the ability to pause
individual deployments while others continue without performing a full
rollback can be helpful.

The `has_decision_point` fields above indicate whether a user has indicated
a desire to pause a deployment at a particular Block.

A Plan's execution may be paused or continued by POST commands executed
against the HTTP endpoints at the [`/v1/plan/interrupt`](https://github.com/mesosphere/dcos-commons/blob/master/src/main/java/org/apache/mesos/scheduler/plan/api/PlanResource.java#L53)
and [`/v1/plan/continue`](https://github.com/mesosphere/dcos-commons/blob/master/src/main/java/org/apache/mesos/scheduler/plan/api/PlanResource.java#L46)
respectively.  For example:

```
POST /v1/plan/interrupt"
```

```
POST /v1/plan/continue"
```

Finally, beyond manual control of Plan execution behavior it may be
desirable in certain failure scenarios to either restart the execution
of a Block or force its completion.  Restarting a Block in default
implementations simply sets a Block's status to `PENDING` and allows the
normal Block processing system to do the necessary work to drive a Block
to completion.  This can be accomplished by issuing a POST command to
the [`/v1/plan/restart`](https://github.com/mesosphere/dcos-commons/blob/master/src/main/java/org/apache/mesos/scheduler/plan/api/PlanResource.java#L69)
endpoint.  For example to restart the Block associated with the first
data task `data-0` one would issue the following command:

```bash
POST /v1/plan/restart?phase=927ac279-b969-4106-9089-40264ae15dc4&block=efcf9dc0-488f-4f9e-ae0c-1ac16c401e0a"
```

Forcing the completion of a block can be accomplished in a similar
manner by issuing a POST command against the [`/v1/plan/forceComplete`](https://github.com/mesosphere/dcos-commons/blob/master/src/main/java/org/apache/mesos/scheduler/plan/api/PlanResource.java#L69)
endpoint. For example to force the completion of the second `meta-data`
block one would issue the following command:

```bash
POST /v1/plan/forceComplete?phase=31a3ca99-bdbd-45dd-a8f0-cc93feade61&block=99210582-cdfb-4b63-95e9-19600f117dc7"
```

# Update configuration

We saw in the [Service Specification
section](#create-a-service-specification) that the service's
configuration is read in through environment variables.  You can use
any source of configuration, but DC/OS Services are typically
configured through environment variables.  To make a configuration
change, modify the corresponding environment variable,
then restart the scheduler.  When the scheduler comes back up, it will
read its new configuration and transition to the new state.

For example, in order to scale the `data-store` service from 3 `data`
nodes to 5, modify the `DATA_COUNT` environment variable. To modify
it:

1) Go the the **Services** > **data-store** page of the DC/OS UI.

1) Click **Edit**.

1) Click the **Environment Variables** tab.

1) Change the value of `DATA_COUNT` from 3 to 5.

1) Click **Deploy Changes**.

This will restart the scheduler with the updated environment.  When
the scheduler starts up, it detects that it is already running 3
`data` tasks and starts 2 more in order to reach the goal state of 5.
We should observe two more tasks starting, `data-3` and `data-4`.

## Task Configuration management

While the above describes configuration of the scheduler itself via
environment variables, there's also a need for configuration of the
underlying service tasks themselves in a flexible way.

To simplify the common task of getting user-facing configuration to
service tasks, the developer may follow the following convention in
naming the environment variables for these configuration options:
`TASKCFG_<TASK_TYPE>_<CFGNAME>`, where `<TASK_TYPE>` has been
converted from the task type to fit the requirements of environment
variables:

- Uppercased
- Non-alphanumeric characters (punctuation, whitespace) converted
  to underscores

For example, an option named `FOO` for tasks of type `index.mgr`
should be named `TASKCFG_INDEX_MGR_FOO`, while an option `BAR` for
tasks of type `data-node` should be named `TASKCFG_DATA_NODE_BAR`.
These configuration options will automatically be forwarded to the
environments of the matching tasks as environment variables, with
the `TASKCFG_<TASK_TYPE>_` prefixes removed. A special prefix of
`TASKCFG_ALL_<NAME>` may be used for any options that should be
passed to *every* task type.

A common need for service developers is an easy way to write
configuration files before launching tasks. To fulfill this need,
the developer may provide configuration file template(s) in their
TaskSet(s). These templates follow the [mustache](https://mustache.github.io/)
templating format, similar to what's used in DC/OS packaging. The
templates will be automatically rendered against the task's
environment (which is customized as described above), and then
each written to relative file paths specified by the developer.

# Restart tasks

When a task fails, the scheduler will attempt to restart the on the
same node, using the same persistent volume as the previous task.

There are two cases where you must manually restart tasks:

1) If an agent permanently fails or is failing, the scheduler will
never receive an
offer with the expected persistent volume, and will thus never
automatically restart the task.  We must restart it manually on a
different node.  This is called *replacing* the task.

2) If a task is stuck or performing poorly, or we just want to restart
it for debugging, we may need to restart the task manually on the same
node.

You can perform both actions using the same endpoint:

```
POST /v1/tasks/restart/<task-name>?replace={true|false}
```

If `replace=true`, this action will replace the task on a separate
node.  If `replace=false`, it will restart the task on the same node.

# Placement

The `DefaultScheduler` makes no intelligent placement decisions.  It
will run a task on first offer it gets that satisfies the resource
constraints for that task.

Placement decisions may be customized using *Placement Constraints*,
which
are rules that specify how tasks should be placed in the cluster.
These rules
are enforced by filtering the resource offers returned by Mesos.
Resources that do not conform to the rules are removed from the list.

You can define placement constraints on a
per-task basis by customizing the return value of
`TaskSpecification.getPlacement()`:

```
public Optional<PlacementRuleGenerator> getPlacement() {
    // avoid systems which are running an "index" task:
    return Optional.of(TaskTypeGenerator.createAvoid("index"));
}
```

You can use some of the common `PlacementRule`s
and/or `PlacementRuleGenerator` implementations provided in the
[offer.constrain](https://github.com/mesosphere/dcos-commons/tree/
master/src/main/java/org/apache/mesos/offer/constrain)
package. You can also create your own `PlacementRule`s for custom
constraints. Custom constraints can take advantage of anything present
in the Mesos `Offers`
or in the other running `TaskInfo`s.

# Logs

The `stdout` and `stderr` streams for all tasks running in Mesos,
including the scheduler and executors, are captured and written to
files in that task's sandbox. You can view these files from the
**Services** tab of the DC/OS UI or from the CLI. [Learn
more](https://docs.mesosphere.com/1.8/administration/logging/service-
logs/).

All code in `dcos-commons`, including the `DefaultScheduler`, uses
`slf4j` to write logs to `stderr`.  To write your own logging code,
first create a `LOGGER`:

```java
private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
```

Then write to it:

```java
LOGGER.info("Starting reference scheduler with args: " + Arrays.asList(args));
```

The contents of `stdout` and `stderr` for the executors is entirely
dependent on the command specified in our `ServiceSpecification`.

# Tests

## Unit Tests

`dcos-commons` contains unit
tests written with `junit` and `mockito`. You can also create your own
unit tests.

## Integration Tests

The [shakedown](https://github.com/dcos/shakedown) library is a tool for
writing integration tests for DC/OS services.  For an example, see
[the tests for DC/OS
Kafka](https://github.com/mesosphere/dcos-kafka-service/tree/master/integration/tests).

# Metrics

As of DC/OS 1.8, this section is only relevant to DC/OS Enterprise
Edition, but support has recently been
[open-sourced](http://github.com/dcos/dcos-metrics)
and should soon be available in Open DC/OS. For more information,
see the [dcos-metrics](http://github.com/dcos/dcos-metrics)
repository and stop by the #day2ops channel on [DC/OS
Slack](https://chat.dcos.io).

DC/OS Metrics automatically provides all Mesos containers with a
unique UDP endpoint for outputting `statsd`-formatted metrics. The
endpoint
is advertised via two container environment variables:
`STATSD_UDP_HOST`
and `STATSD_UDP_PORT`. Any metrics sent to this advertised endpoint
will automatically be tagged with the originating container and
forwarded upstream to the cluster's metrics infrastructure.

You can take advantage of this endpoint for your service
scheduler as well as for the underlying service tasks themselves. For
instance, you can configure your service's tasks to emit to the
locally-advertised endpoint as they are launched.

**Note:** The environment-advertised endpoint is
unique to each container and cannot be reused across containers.

The data sent to this endpoint should follow the standard `statsd`
text format, optionally with multiple newline-separated values
per UDP packet.
[http://docs.datadoghq.com/guides/dogstatsd/#datagram-format](Datadog-
extension)
tags are also supported, so the application may also include its own
custom tags:

```
memory.usage_mb:5|g
frontend.query.latency_ms:46|g|#shard_id:6,section:frontpage
```

See also:
- [dcos-metrics repo](https://github.com/dcos/dcos-metrics)
- [Sample StatsD emitter
process](https://github.com/dcos/dcos-metrics/tree/master/examples/
statsd-emitter)
- [Metrics configuration for
Kafka](https://github.com/mesosphere/dcos-kafka-service/blob/
30acc60676ba9362ddb9b74f208b36d257a78f93/kafka-config-overrider/src/
main/java/com/mesosphere/dcos/kafka/config/Overrider.java#L163)
- [Metrics configuration for
Cassandra](https://github.com/mesosphere/dcos-cassandra-service/blob/
38360f9f78d7063824ad77f9871108fe5609e54d/cassandra-executor/src/main/
java/com/mesosphere/dcos/cassandra/executor/metrics/MetricsConfig.java
#L68)

# Service Discovery

Mesos-DNS assigns every Mesos task a DNS address of the form
`<task-name>.<framework-name>.mesos`.  In our example (running 3 data
nodes), Mesos-DNS creates the following addresses:

```
meta-data-0.data-store.mesos
meta-data-1.data-store.mesos
meta-data-2.data-store.mesos
data-0.data-store.mesos
data-1.data-store.mesos
```

It also creates a DNS entry for the scheduler:

```
data-store.marathon.mesos
```

If the tasks listen on reserved port(s), clients will also need
these port(s) in order to establish a connection.  We can fetch these
port(s) along with the DNS entry for a task via the following
endpoint:

```bash
GET /v1/tasks/connection/{task-name}
```

This HTTP request returns:

```json
{
  "dns": "data-0.data-store.mesos",
  "ports": "4388"
}
```

If the task has multiple ports reserved, they will be displayed as a
comma-delimited sequence of ranges. e.g. `"8080,2000-3000"`.

# Secrets Management

This section is only relevant to DC/OS Enterprise Edition.

In a DC/OS Enterprise Edition cluster running in strict security mode,
all schedulers must authenticate with the Mesos master using DC/OS
service accounts.  For instructions on creating a service account,
read
[this][https://docs.mesosphere.com/1.8/administration/id-and-access-
mgt/service-auth/]

Mesos schedulers authenticate to the master by providing a `principal`
and `secret`.  In DC/OS, the `principal` must be the DC/OS Service
Account ID, and the `secret` must be the private key.  To start the
scheduler with access to the private key, we must integrate with
Marathon's DC/OS secret support:

```javascript
{
  "env": {
    "DCOS_SERVICE_ACCOUNT_CREDENTIAL": {"secret": "serviceCredential"}
  },
  "secrets": {
    "serviceCredential": {
      "source": <secret_name>
    }
  }
}
```

This causes Marathon to launch the task with the
`DCOS_SERVICE_ACCOUNT_CREDENTIAL` environment variable set to the
contents of the service account's private key.  The Mesos agent looks
for this environment variable and will inject it as the `secret` in
the scheduler's registration request.

# HTTP API

Services expose an HTTP API to support common operations.  There are
two ways to access this API.

The first is from clients running outside of DC/OS, such as from your
local machine.  These clients must authenticate.  For example:

```bash
curl -H "Authorization: token=$(dcos config show core.dcos_acs_token)" \
    "$(dcos config show core.dcos_url)/service/data-store/v1/plan/status"
```

To learn more about DC/OS Authentication, visit the
[Managing
Authentication](https://dcos.io/docs/1.8/administration/id-and-access-
mgt/managing-authentication/)
section of the DC/OS documentation.

The second way to access the HTTP API is from clients running inside
the
DC/OS cluster.  These clients may bypass DC/OS authentication and
access the scheduler directly:

```bash
curl "data-store.marathon.mesos:<port>/v1/plan/status"
```

Where <port> is the port you gave to the `DefaultService` constructor
in the [Define a Service](#define-a-service) section.

Several API endpoints are listed in this tutorial and a full API is
forthcoming.

# Features at-a-glance

- **Simple service definitions** - A simple, declarative API to
simplify resource offer configuration. [Learn
more](#simple-service-definitions).

- **Multi-tier service support** - A scheduler created with the DC/OS
Stateful Services SDK is aware of dependencies between tiers of
processes, enabling one-click install and safe failure recovery.
[Learn more](#multi-tier-service-support).

- **Deployment strategies** - The DC/OS Stateful Services SDK supports
diverse deployment strategies to fit the needs of schedulers. [Learn
more](#deployment-strategies).

- **Configuration management** - The DC/OS Stateful Services SDK can
read
configuration from an external source, detect configuration
modifications, and control which configurations can be modified by the
user. [Learn more](#configuration-management).

- **Interactive upgrade support** - You can pause, continue, or abort
an upgrade interactively in order to upgrade tasks safely. [Learn
more](#interactive-upgrade-support).

- **Fault tolerance** - Schedulers built with the SDK are
automatically configured to detect and
restart failed tasks on the same node (to preserve state) and, if the
node fails, restart it on a different node. [Learn
more](#fault-tolerance).

- **Persistent volumes** - The SDK automatically configures your
scheduler to
create persistent volumes, enabling stateful tasks. [Learn
more](#persistent-volumes).

# Features in-depth

## Simple service definitions
Mesos offers resources to the scheduler for launching tasks rather
than launching tasks against a fixed API. Resource offers are more
powerful and flexible than a declarative task launching API because
schedulers can apply arbitrary logic to determine the set of resources
they consume.

However, the majority of schedulers, especially schedulers for
stateful services, only need a basic API. For these cases,
declarative APIs are much simpler.  A simple scheduler only needs to
run N containers with M memory and C cpu, along with some set of
persistent volumes, ports, etc.

For these cases, the DC/OS Stateful Services SDK provides a simple,
declarative
API built on top of Mesos resource offers. You define your service
declaratively and the SDK manages installing and supervising the
service using resource offers from Mesos.

You can think of this as doing for stateful services what Marathon
does for stateless services, while still allowing you to drop down to
lower layers when you need more flexibility.

## Multi-tier service support
Many stateful services have multiple interdependent tiers,
each running different processes.  For example, Apache HDFS is
composed of NameNodes, JournalNodes, and DataNodes. JournalNodes start
before NameNodes, which start before DataNodes.  To support one-click
install for users, as well as safe recovery from failures, the
scheduler must be aware of these dependencies.

## Deployment strategies
Many stateful services have strict deployment requirements.  Some,
including, for instance Apache Cassandra, require nodes to be added to
the cluster one at at time.  Others may permit parallel deployment.
The DC/OS Stateful Services SDK includes deployment strategies
that support these different requirements.

## Configuration management

For any framework, at least two components must be configured: the
scheduler and the service.  Scheduler configuration includes things
like node count, deployment strategies, and security parameters.
Service configuration includes resource settings such memory, CPU, and
ports, as well as any configuration passed on to the underlying
service.

The DC/OS Stateful Services SDK includes logic for reading
configuration from an external source (default: environment
variables), detecting changes to the configuration, and redeploying
any affected tasks.  It also supports marking certain configuration
parameters as "unmodifiable," so that the user can't change them after
install time.  For example, the disk size of permanent volumes cannot
be modified because volume size is static.

## Interactive upgrade support
Tasks must often be updated one at a time, and often depend on certain
administrative tasks like backup/restore.  You can choose to pause,
continue, or abort an upgrade interactively.

## Fault tolerance
There are two component failures that any scheduler must defend
against: the scheduler and the service. The SDK sets up fault
tolerance for your scheduler automatically.

The SDK abstracts away task reconciliation with the Mesos master in
the case of task failure. In the case of service failure, the SDK
configures your scheduler to restart the task on the same node or, if
the node has failed, restarts the task on a different node. The
scheduler's persistent volume is reused on restart. A new persistent
volume is created when a node fails.

The mechanism to determine whether a task has permanently failed is time-based by default.
When a task stays in a terminal state for some
configurable duration, it is determined to have failed. The default
duration is 20 minutes.  Once tasks have been determined to have
permanently failed, a second configurable parameter determines how many 
destructive task replacements may occur in a given time period.  By 
default, no more than one task may be destructively replaced in any 10 
minute period.  Finally, automatic destructive recovery may be entirely
suppressed.

Consider the simplest construct for the `DefaultScheduler`, in which
automatic destructive recovery configuration is exposed:

```java
DefaultScheduler create(
        String frameworkName,
        PlanManager deploymentPlanManager,
        Optional<Integer> permanentFailureTimeoutSec,
        Integer destructiveRecoveryDelaySec) {
```

The `permanentFailureTimeoutSec` argument determines how long a task
must be in a terminal state before it is considered permanently failed.
If no value is present, automatic destructive recovery is turned off.

The `destructiveRecoveryDelaySec` argument determines how much time must
pass between destructive task replacement events.

The parameters above are one implementation of a more general
permanent task recovery scheme.  The task recovery scheme has two major components: a _safety
constraint_ and a _performance constraint_.  In the example above, the
duration that must be waited until a task is considered permanently 
failed is the _safety constraint_. You can write a safety constraint that is more complex than a simple timeout.

In the example above, the recovery delay
parameter is the _performance constraint_.  Even if a large number of tasks could be safely
destructively replaced, it could cause performance degradation
as network traffic increases to reconstruct lost task state. In addition, you must be cautious when automating destructive recovery operations, so it is prudent to throttle the maximum rate of
destruction.

Recovery, like deployment, is mediated by a plan. Use the endpoint below to view the status of the plan.
```bash
GET /v1/plans/recovery
```

If no failures of any kind have occurred, you will see output similar to the snippet below.
```json
{
	phases: [{
		id: "128d7df9-8605-4e1a-b98b-478821b1aeda",
		name: "recovery",
		steps: [],
		status: "COMPLETE"
	}],
	errors: [],
	status: "COMPLETE"
}
```

If a task has crashed and been recovered, you will see a plan similar
to:
```json
{
	phases: [{
		id: "88d944d3-4fc2-4605-889f-96b5429fb8af",
		name: "recovery",
		steps: [{
			id: "ed4d2209-ab19-4242-b7da-d10cdf9e443b",
			status: "COMPLETE",
			name: "data-2",
			message: "org.apache.mesos.scheduler.recovery.DefaultRecoveryBlock: 'data-2 [ed4d2209-ab19-4242-b7da-d10cdf9e443b]' has status: 'COMPLETE'. RecoveryType: TRANSIENT"
		}],
		status: "COMPLETE"
	}],
	errors: [],
	status: "COMPLETE"
}
```

Note in particular the message that contains
`RecoveryType: TRANSIENT` for the step named `data-2`.  This message indicates a
recovery from a temporary failure.  The task was able to be successfully
recovered in its previous location with all its old resources, including
persistent volumes.  In the case of a permanent failure recovery, the
message would instead contain `RecoveryType: PERMANENT`.

## Persistent volumes
Schedulers must create persistent volumes that will
live beyond the life a single task to tolerate failure, and they must
reserve these volumes to prevent other frameworks from taking them.
The SDK configures persistent volumes for your scheduler automatically.
