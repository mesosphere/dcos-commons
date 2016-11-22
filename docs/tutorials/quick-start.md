## DC/OS SDK Quick Start

The goal of this quick-start guide is to introduce key concepts which we'll use for modeling real stateful services.

In this tutorial, we'll build a `hello-world` service. The `hello-world` service will be composed of 2 instances of
`helloworld` pod, each running a single `server` task. Let's get started by declaratively modeling our service using 
a YAML specification file:

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
        cmd: "echo 'Hello World!' >> helloworld-container-volume/output && sleep 1"
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
* Configured the API port using `api-port: 8080`. By default, each service comes along with a default set of useful APIs which 
enables operationalization. 
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
    cmd: "echo 'Hello World!' >> helloworld-container-volume/output && sleep 1"
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

