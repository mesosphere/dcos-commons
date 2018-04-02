<p align="left">
  <img src="https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/dcos-sdk-logo.png" width="250"/>
</p>

[__Quick Start__](README.md#quick-start) |
[__Developer Guide__](https://mesosphere.github.io/dcos-commons/developer-guide/) |
[__FAQ__](docs/pages/faq.md) |
[__Javadocs__](https://mesosphere.github.io/dcos-commons/reference/api/) |
[__Contributing__](CONTRIBUTING.md) |
[__Slack__](http://chat.dcos.io)

---
__DC/OS SDK__ is a collection of tools, libraries, and documentation for easy integration of technologies such as Kafka, Cassandra, HDFS, Spark, and TensorFlow with DC/OS.

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
* [Developer Guide](https://mesosphere.github.io/dcos-commons/developer-guide/)
* [Javadocs](https://mesosphere.github.io/dcos-commons/reference/api/)

---
### Contributions
Contributions are welcome! See [CONTRIBUTING](CONTRIBUTING.md).

---
### License
DC/OS SDK is licensed under the Apache License, Version 2.0.
