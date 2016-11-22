## DC/OS SDK Quick Start

### Overview
The goal of this quick-start guide is to introduce key concepts which we'll use for modeling real stateful services.

In this tutorial, we'll build a `hello-world` service. The `hello-world` service will be composed of a single instance of
`helloworld` pod, running a single `server` task.

### Step 1 - Initialize service project
Get started by forking: https://github.com/mesosphere/dcos-commons, and cloning it on your workstation. Change your working directory to `dcos-commons` and then issue following command to create a new project:

```bash
./new-service.sh frameworks/hello-world
```

Above command will generate a new project at location `frameworks/hello-world/`:

```bash
$ ls -l frameworks/hello-world/
total 12
-rw-r--r--  1 mohit staff  68 Nov 22 14:49 README.md
drwxr-xr-x 12 mohit staff 408 Nov 18 13:47 build
-rw-r--r--  1 mohit staff 876 Nov 22 14:49 build.gradle
-rwxr-xr-x  1 mohit staff 487 Nov 22 14:49 build.sh
drwxr-xr-x  3 mohit staff 102 Nov 22 14:49 cli
drwxr-xr-x  4 mohit staff 136 Nov 22 14:49 integration
drwxr-xr-x  4 mohit staff 136 Nov 22 14:49 src
drwxr-xr-x  7 mohit staff 238 Nov 22 14:49 universe
```

### Step 2 - Declarative YAML Service Specification
Let's get started by declaratively modeling our service using a YAML specification file. Please create a file `service.yml` inside your project's `src/main/dist` directory:

```yaml
name: "hello-world"
principal: "hello-world-principal"
zookeeper: master.mesos:2181
api-port: 8080
pods:
  helloworld:
    count: 2
    tasks:
      server:
        goal: RUNNING
        cmd: "echo 'Hello World!' >> helloworld-container-volume/output && sleep 10"
        cpus: 0.5
        memory: 32
        volumes:
          - path: "helloworld-container-volume"
            type: ROOT
            size: 64
```

In above specification file, we have:
* Defined a service with name `hello-world`
* Configured the service to use zookeeper at `master.mesos:2181` for storing framework state and configuration.
* Configured the API port using `api-port: 8080`. By default, each service comes along with a default set of useful APIs which enables operationalization. 
* Defined a pod specification for our `helloworld` pod using:

```yaml
pods:
  helloworld:
    ...
```
* Configured that we need atleast 2 instances of `helloworld` pod running at all times.
* Defined a task specification for our `server` task using:

```yaml
tasks:
  server:
    goal: RUNNING
    cmd: "echo 'Hello World!' >> helloworld-container-volume/output && sleep 10"
    cpus: 0.5
    memory: 32
```
configuring it to use `0.5` CPUs and `32 MB` of memory.
* And finally, configured a `64 MB` persistent volume for our server task where the task data can be persisted using:

```yaml
volumes:
  - path: "helloworld-container-volume"
    type: ROOT
    size: 64
```

### Step 2 - Declarative Java Service Specification

Alternatively, you can define `hello-world` service specification in Java using:
```java
ServiceSpec helloWorldSpec = DefaultServiceSpec.newBuilder()#
  .name("hello-world")
  .principal("helloworld-principal")
  .zookeeperConnection("master.mesos:2181")
  .apiPort(8080)
  .addPod(DefaultPodSpec.newBuilder()
    .count(2)
    .addTask(DefaultTaskSpec.newBuilder()
      .name("server")
      .goalState(TaskSpec.GoalState.RUNNING)
      .commandSpec(DefaultCommandSpec.newBuilder()
        .value("echo 'Hello World!' >> helloworld-container-volume/output && sleep 10")
        .build())
      .resourceSet(DefaultResourceSet
        .newBuilder("helloworld-role", "helloworld-principal")
        .id("helloworld-resources")
        .cpus(1.0)
        .memory(32.0)
        .addVolume("ROOT", 64.0, "helloworld-container-path")
        .build()).build()).build()).build();
```
TODO(mohit): Introduce concept of resource sets.

### Step 3 - Writing executable class

To make the declarative service specification executable, we need to initialize an instance of `Service` with the specification. For example:

#### YAML Specification

We can create an instance of `DefaultService` with the path of `service.yml` as following example demonstrates:
```java
package com.mesosphere.sdk.helloworld.scheduler;

import org.apache.mesos.specification.DefaultService;

import java.io.File;

/**
 * Main using YAML specification.
 */
public class Main {
    public static void main(String[] args) throws Exception {
      File serviceSpecYamlPath = new File(args[0]);
      Service service = new DefaultService(serviceSpecYamlPath);
    }
}
```

#### Java Specification
Alternatively, we can create an instance of `DefaultService` with the POJO service specification as following example demonstrates:

```java
package com.mesosphere.sdk.helloworld.scheduler;

import org.apache.mesos.specification.DefaultService;

import java.io.File;

/**
 * Main using Java specification.
 */
public class Main {
    public static void main(String[] args) throws Exception {
      ServiceSpec helloWorldSpec = DefaultServiceSpec.newBuilder()#
        .name("hello-world")
        .principal("helloworld-principal")
        .zookeeperConnection("master.mesos:2181")
        .apiPort(8080)
        .addPod(DefaultPodSpec.newBuilder()
          .count(2)
          .addTask(DefaultTaskSpec.newBuilder()
            .name("server")
            .goalState(TaskSpec.GoalState.RUNNING)
            .commandSpec(DefaultCommandSpec.newBuilder()
              .value("echo 'Hello World!' >> helloworld-container-volume/output && sleep 10")
              .build())
            .resourceSet(DefaultResourceSet
              .newBuilder("helloworld-role", "helloworld-principal")
              .id("helloworld-resources")
              .cpus(1.0)
              .memory(32.0)
              .addVolume("ROOT", 64.0, "helloworld-container-path")
              .build()).build()).build()).build();
      Service service = new DefaultService(helloWorldSpec);
    }
}
```

And, now we are ready to take the `helloworld` service for a spin.

### Step 4 - Build and Run
