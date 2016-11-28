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
The __DC/OS SDK__ is a collection of tools, libraries, and documentation for easy integration and automation of replicated stateful services, such as databases, message brokers, and caching services, with [DC/OS](https://dcos.io/).

### Benefits

* __Simple and Flexible__: The SDK provides the simplicity of a declarative YAML API as well as the flexibility to use the full Java programming language.

* __Automate Maintenance__: Stateful services need to be maintained. With the SDK, you can automate maintenance routines, such as backup and restore, to simplify operations.

* __Available and Durable__: When servers fail, you need to reschedule the tasks without data loss or performance impact. With the SDK, you develop automated recovery strategies so services heal themselves.

* __Production-Proven__: Building reliable services is hard. Uber and Bing platform teams use the SDK for mission-critical databases and message brokers.

===============
### Quick Start

From a workstation with 8G Memory, [Git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git), [VirtualBox](https://www.virtualbox.org/wiki/Downloads), and [Vagrant](https://www.vagrantup.com/downloads.html), run:

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
