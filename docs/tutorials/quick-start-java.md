## Quick Start (Java)

### Overview
The goal of this quick-start guide is to introduce key concepts which we'll use for modeling real stateful services using the Java interface.

In this tutorial, we'll build our `helloworld` service using the Java interface. The `helloworld` service will be composed of a single instance of `helloworld` pod, running a single `server` task.

### Step 0 - Clone the project
Get started by forking: https://github.com/mesosphere/dcos-commons, and cloning it on your workstation. Change your working directory to `dcos-commons`.

```bash
$ cd dcos-commons
(dcos-commons)$ 
```

### Step 1 - Provision Dev DC/OS Cluster
Let's provision a DC/OS cluster for the purposes of development. You can install one locally on your developer workstation using the bundled Vagrant setup which can be initialized as follows:

```bash
(dcos-commons)$ ./get-dcos-docker.sh
```

Visit the DC/OS cluster [dashboard](http://172.17.0.2/) to verify your development environment is running.

Next, ssh into the vagrant box:

```bash
(dcos-commons)$ cd tools/vagrant/ && vagrant ssh
```

And, finally change the directory to `/dcos-commons`, this is where the source code from your host is mounted inside the Vagrant box:

```bash
$ cd /dcos-commons/
(dcos-commons)$ 
```

### Step 2 - Initialize service project
Get started by forking: https://github.com/mesosphere/dcos-commons, and cloning it on your workstation. Change your working directory to `dcos-commons` and then issue following command to create a new project:

```bash
(dcos-commons)$ ./new-service.sh frameworks/helloworldjava
```

Above command will generate a new project at location `frameworks/helloworldjava/`:

```bash
(dcos-commons)$ ls -l frameworks/helloworldjava/
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

### Step 3 - Declarative Java Service Specification

Let's define `helloworldjava` service specification in Java using:
```java
ServiceSpec helloWorldSpec = DefaultServiceSpec.newBuilder()
  .name("helloworldjava")
  .principal("helloworldjava-principal")
  .zookeeperConnection("master.mesos:2181")
  .apiPort(8080)
  .addPod(DefaultPodSpec.newBuilder()
    .count(Integer.parseInt(System.getenv("COUNT")))
    .addTask(DefaultTaskSpec.newBuilder()
      .name("server")
      .goalState(TaskSpec.GoalState.RUNNING)
      .commandSpec(DefaultCommandSpec.newBuilder()
        .value("echo 'Hello World!' >> helloworld-java-container-volume/output && sleep 10")
        .build())
      .resourceSet(DefaultResourceSet
        .newBuilder("helloworldjava-role", "helloworld-java-principal")
        .id("helloworld-java-resources")
        .cpus(Double.parseDouble(System.getenv("SERVER_CPU")))
        .memory(32.0)
        .addVolume("ROOT", 64.0, "helloworldjava-container-volume")
        .build()).build()).build()).build();
```

In above specification, we have:
* Defined a service with name `helloworld-java` using `.name("helloworld-java")`
* Configured the service to use zookeeper at `master.mesos:2181` for storing framework state and configuration using `.zookeeperConnection("master.mesos:2181")`
* Configured the API port using `.apiPort(8080)`. By default, each service comes along with a default set of useful APIs which enables operationalization. 
* Defined a pod specification for our `helloworld` pod using:

```java
.addPod(DefaultPodSpec.newBuilder()
    .count(Integer.parseInt(System.getenv("COUNT")))
    .addTask(DefaultTaskSpec.newBuilder()
    ...
```
* Configured that we need atleast `COUNT` instances of `helloworld` pod running at all times.
* Defined a task specification for our `server` task using:

```java
.addTask(DefaultTaskSpec.newBuilder()
  .name("server")
  .goalState(TaskSpec.GoalState.RUNNING)
```
configuring it's goal state to be `RUNNING` which is an indication for the Service scheduler to keep the task up all the time.
* Defined a command specification for our `server` task, which specifies the command that needs to be executed when starting the `server` task:

```java
.commandSpec(DefaultCommandSpec.newBuilder()
  .value("echo 'Hello World!' >> helloworldjava-container-volume/output && sleep 10")
  .build())
```
* And finally, defined a resource set for our task, which contains the resources that are required for the `server` task to run:

```java
.resourceSet(DefaultResourceSet
  .newBuilder("helloworldjava-role", "helloworldjava-principal")
  .id("helloworld-java-resources")
  .cpus(Double.parseDouble(System.getenv("SERVER_CPU")))
  .memory(32.0)
  .addVolume("ROOT", 64.0, "helloworldjava-container-volume")
```
The above resource set configures the cpu shares using `SERVER_CPU` environment variable, configures `32` MB of memory, and also configures the task with `64` MB of persistent volume mounted inside the task sandbox at path `helloworld-java-container-volume`.

### Step 4 - Writing executable class

To make the above declarative service specification executable, we need to initialize an instance of `Service` with it. In order to do that let's modify the `Main` class located at: `src/main/java/com/mesosphere/sdk/helloworld/scheduler/Main.java`

```java
package com.mesosphere.sdk.helloworld.scheduler;

import org.apache.mesos.specification.DefaultService;

import java.io.File;

/**
 * Main using Java specification.
 */
public class Main {
    public static void main(String[] args) throws Exception {
      ServiceSpec helloWorldSpec = DefaultServiceSpec.newBuilder()
        .name("helloworldjava")
        .principal("helloworldjava-principal")
        .zookeeperConnection("master.mesos:2181")
        .apiPort(8080)
        .addPod(DefaultPodSpec.newBuilder()
          .count(Integer.parseInt(System.getenv("COUNT")))
          .addTask(DefaultTaskSpec.newBuilder()
            .name("server")
            .goalState(TaskSpec.GoalState.RUNNING)
            .commandSpec(DefaultCommandSpec.newBuilder()
              .value("echo 'Hello World!' >> helloworldjava-container-volume/output && sleep 10")
              .build())
            .resourceSet(DefaultResourceSet
              .newBuilder("helloworldjava-role", "helloworldjava-principal")
              .id("helloworldjava-resources")
              .cpus(Double.parseDouble(System.getenv("SERVER_CPU")))
              .memory(32.0)
              .addVolume("ROOT", 64.0, "helloworldjava-container-volume")
              .build()).build()).build()).build();
      Service service = new DefaultService(helloWorldSpec);
    }
}
```

And, now we are ready to take the `helloworldjava` service for a spin.

### Step 5 - Build
Let's build our project by running `./build.sh local` from `/dcos-commons/frameworks/helloworldjava` directory:

```bash
(dcos-commons/frameworks/helloworldjava)$ ./build.sh local
```

Running above command will generate a deployable package and make it available for deployment inside the locally running DC/OS cluster.

### Step 6 - Run
Finally, let's install the `helloworldjava` project using:

```bash
dcos package install helloworldjava
```

Visit the [dashboard](http://172.17.0.2/#/services/%2Fhelloworldjava/) to see your `helloworldjava` service running.
