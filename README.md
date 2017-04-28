<p align="left">
  <img src="https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/dcos-sdk-logo.png" width="250"/>
</p>

[__Quick Start__](README.md#quick-start) |
[__Developer Guide__](https://mesosphere.github.io/dcos-commons/developer-guide.html) |
[__FAQ__](docs/pages/faq.md) |
[__Javadocs__](https://mesosphere.github.io/dcos-commons/api/) |
[__Contributing__](CONTRIBUTING.md) |
[__Slack__](http://chat.dcos.io)

---
__DC/OS SDK__ is a collection of tools, libraries, and documentation for easy integration and automation of stateful services, such as databases, message brokers, and caching services.

![Status](https://img.shields.io/badge/Status-Alpha-BF97F0.svg?style=flat-square)

DC/OS SDK is currently in alpha stage: it can run services, but APIs change regularly, and features are under active development.

### Benefits

* __Simple and Flexible__: The SDK provides the simplicity of a declarative YAML API as well as the flexibility to use the full Java programming language.

* __Automate Maintenance__: Stateful services need to be maintained. With the SDK, you can automate maintenance routines, such as backup and restore, to simplify operations.

* __Production-Proven__: Building reliable services is hard. Uber and Bing platform teams use the SDK for mission-critical databases and message brokers.

---
### Quick Start

From a workstation with 8GB Memory, [Git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git), [VirtualBox 5.0.x](https://www.virtualbox.org/wiki/Download_Old_Builds_5_0), and [Vagrant 1.8.4](https://releases.hashicorp.com/vagrant/1.8.4/):

1. Download the DC/OS SDK.
  ```
  git clone https://github.com/mesosphere/dcos-commons.git
  ```

2. Create your local development environment.
  ```
  cd dcos-commons/ && ./get-dcos-docker.sh
  ```
  * Visit the DC/OS cluster [dashboard](http://172.17.0.2/) to verify your development environment is running.

3. Enter your development environment.
  ```
  cd tools/vagrant/ && vagrant ssh
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

---
### Understanding the Hello World Service Specification

The service specification declaratively defines the `helloworld` service:

```yaml
name: "helloworld"
pods:
  helloworld:
    count: {{COUNT}}
    tasks:
      server:
        goal: RUNNING
        cmd: "echo 'Hello World!' >> helloworld-container-volume/output && sleep 10"
        cpus: {{SERVER_CPU}}
        memory: 32
        volume:
          path: "helloworld-container-volume"
          type: ROOT
          size: 64
```

In the above YAML file, we have:
* Defined a service with the name `helloworld`
* Defined a pod specification for our `helloworld` pod using:

```yaml
pods:
  helloworld:
    count: {{COUNT}}
    tasks:
      ...
```
* Declared that we need atleast `{{COUNT}}` instances of the `helloworld` pod running at all times, where `COUNT` is the environment variable that is injected into the scheduler process at launch time via Marathon. It defaults to `1` for this example.
* Defined a task specification for our `server` task using:

```yaml
tasks:
  server:
    goal: RUNNING
    cmd: "echo 'Hello World!' >> helloworld-container-volume/output && sleep 10"
    cpus: {{SERVER_CPU}}
    memory: 32
```
We have configured it to use `{{SERVER_CPU}}` CPUs (which defaults to `0.5` for this example) and `32 MB` of memory.
* And finally, configured a `64 MB` persistent volume for our server task where the task data can be persisted using:

```yaml
volume:
  path: "helloworld-container-volume"
  type: ROOT
  size: 64
```

---
### References
* [Quick Start Guide - Java](https://mesosphere.github.io/dcos-commons/tutorials/quick-start-java.html)
* [Developer Guide](https://mesosphere.github.io/dcos-commons/developer-guide.html)
* [Javadocs](https://mesosphere.github.io/dcos-commons/api/)

---
### Contributions
Contributions are welcome! See [CONTRIBUTING](CONTRIBUTING.md).

---
### License
DC/OS SDK is licensed under the Apache License, Version 2.0.
