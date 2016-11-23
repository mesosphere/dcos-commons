<p align="left">
  <img src="https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/dcos-sdk-logo.png" width="250"/>
</p>
![Status](https://img.shields.io/badge/Status-Alpha-BF97F0.svg?style=flat-square)

[__Quick Start__](README.md#quick-start) |
[__FAQ__](docs/faq.md) |
[__Javadocs__](http://mesosphere.github.io/dcos-commons/api/) |
[__Contributing__](CONTRIBUTING.md) |
[__Slack__](http://chat.dcos.io)

=========
[DC/OS](https://dcos.io) is a datacenter operating system for running distributed services, such as databases. Like most operating systems, DC/OS has a package manager and package repository, so services can be installed with 1-click.

The __DC/OS SDK__ is a collection of tools, libraries, and documentation for packaging services for DC/OS. With the SDK, you can easily integrate stateful services developed in any programing language.
 
### Benefits

* __Simple and Flexible__: The SDK provides the simplicity of a declarative YAML API as well as the flexibility to use the full Java programming language. 

* __Automate Maintenance__: Some services, such as databases, need to be maintained. With the SDK, you can automate maintenance routines to simplify operations.

* __Available and Durable__: When infrastructure fails, you need to recover without data loss or performance impact. With the SDK, you define custom detection, safety, and performance semantics so your services heal themselves.

* __Production-Proven__: Building reliable distributed services is hard. Some of the most demanding web services in the world use services built with the SDK for their mission-critical databases and messaging systems.

===============
### Quick Start

From a workstation with 8G Memory, [Git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git), [VirtualBox](https://www.virtualbox.org/wiki/Downloads), and [Vagrant](https://www.vagrantup.com/downloads.html), run:

1. Download the DC/OS SDK.
  ```
  git clone https://github.com/mesosphere/dcos-commons.git
  ```
  
2. Create your local development environment.
  ```
  cd dcos-commons/ && ./build-dcos-docker.sh
  ```
  * Visit the DC/OS cluster [dashboard](http://172.17.0.2/) to verify your development environment is running.

3. Enter your development environment.
  ```
  cd dcos-docker/ && vagrant ssh
  ```
  
4. Build your hello-world example project.
  ```
  cd /dcos-commons/frameworks/helloworld/ && ./build.sh local
  ```
  
5. Start your hello-world DC/OS service.
  ```
  dcos package install hello-world
  ```
  
6. Explore your hello-world service.
  * Visit the [dashboard](http://172.17.0.2/#/services/%2Fhello-world/) to see your hello-world service running.
  * Click through to one of your tasks (e.g. `world-server-1-<uuid>`), select the __Files__ tab, select __world-container-path__, and finally select the __output__ file.

===============
### Understanding Hello World Service Specification

Let's understand the service specification which was used to declaratively define `helloworld` service:

```yaml
name: "helloworld"
principal: "helloworld-principal"
zookeeper: master.mesos:2181
api-port: 8080
pods:
  helloworld:
    count: {{COUNT}}
    tasks:
      server:
        goal: RUNNING
        cmd: "echo 'Hello World!' >> helloworld-container-volume/output && sleep 10"
        cpus: {{SERVER_CPU}}
        memory: 32
        volumes:
          - path: "helloworld-container-volume"
            type: ROOT
            size: 64
```

In above specification file, we have:
* Defined a service with name `helloworld`
* Configured the service to use zookeeper at `master.mesos:2181` for storing framework state and configuration.
* Configured the API port using `api-port: 8080`. By default, each service comes along with a default set of useful APIs which enables operationalization. 
* Defined a pod specification for our `helloworld` pod using:

```yaml
pods:
  helloworld:
    ...
```
* Configured that we need atleast `{{COUNT}}` instances of `helloworld` pod running at all times. Where `COUNT` is the environment variable that is injected into the scheduler process at launch via Marathon. It defaults to `1` for this example.
* Defined a task specification for our `server` task using:

```yaml
tasks:
  server:
    goal: RUNNING
    cmd: "echo 'Hello World!' >> helloworld-container-volume/output && sleep 10"
    cpus: {{SERVER_CPU}}
    memory: 32
```
configuring it to use `{{SERVER_CPU}}` CPUs (defaults to `0.5` for this example) and `32 MB` of memory.
* And finally, configured a `64 MB` persistent volume for our server task where the task data can be persisted using:

```yaml
volumes:
  - path: "helloworld-container-volume"
    type: ROOT
    size: 64
```

===============
### References
* Developer Guide ... *coming soon!*
* [Javadocs](http://mesosphere.github.io/dcos-commons/api/index.html)

===============
### Contributions
Contributions are welcome! See [CONTRIBUTING](CONTRIBUTING.md).

===============
### License
DC/OS SDK is licensed under the Apache License, Version 2.0.
