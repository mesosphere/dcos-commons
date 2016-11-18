Frequently Asked Questions
======================

* __What is the DC/OS SDK?__

  The DC/OS SDK is a collection of tools, libraries, and documentation for integrating stateful services with DC/OS. 

* __What is a Mesos framework?__

  In Mesos, a framework is the component responsible for the second level of scheduling in the [Apache Mesos two-level scheduler architecture](http://mesos.apache.org/documentation/latest/architecture/). Strictly speaking, the SDK is used to build Mesos frameworks, which appear as “services” in DC/OS.


* __What is a DC/OS service?__

  [A DC/OS service](https://dcos.io/docs/1.8/overview/concepts/#dcos-service) is an instance of your service in a DC/OS cluster. 


* __What is a DC/OS package?__

  A DC/OS package is an artifact for installing your service to any DC/OS cluster. DC/OS packages are analogous in concept to other operation systems packages like RPMs and DEBs. A DC/OS package includes metadata such as name, version, configurable settings, and service executables.
  
* __When should I use the DC/OS SDK?__

  The SDK is particularly well-suited to stateful services such as Apache Kafka, Apache Cassandra, and Elasticsearch. Independent software vendors (ISVs) interested in offering production-grade stateful services for DC/OS should use the SDK.


* __What are the advantages of using the SDK vs. building a framework from scratch?__

  The SDK dramatically simplifies integrating stateful services with Apache Mesos and DC/OS so that you can focus on your goals. In the past, integrating existing services with Apache Mesos meant developing a scheduler to evaluate and accept resource offers, launch and monitor tasks, and handle task reconciliation. Stateful services introduce additional scheduler concerns like reserving and accounting for resources such as persistent volumes and more complicated failure recovery semantics. Prior to the SDK, most stateful services had tens of thousands of lines of code requiring months of development and maintenance. With the SDK, even the most complex distributed stateful services require only a few hundred lines of code to integrate with DC/OS.

* __Do I need to be a Java developer to use the DC/OS SDK?__

  No, the SDK offers a YAML interface suitable for simple integrations. Basic Java development skills are necessary for more complex integrations.

* __How long will it take to build my DC/OS service?__

  A developer with basic Java experience will find it easy to use the SDK to integrate existing services with DC/OS. A basic integration generally takes 1-3 days to develop, test, and release. Advanced integrations that include custom service lifecycle plans and automated failed task recovery strategies usually take around 2-3 weeks to develop, test, and release.

* __How can I distribute my DC/OS package?__

  You have can distribute your DC/OS package in several ways:
  
  When you want to maximize reach, [DC/OS Universe](https://github.com/mesosphere/Universe) is the recommended distribution channel. DC/OS Universe is an online repository of DC/OS packages. Packages in Universe are available to all community users and commercial customers. DC/OS packages are installed from DC/OS Universe with 1 click to any DC/OS cluster.
  
  When you need to control distribution, we recommend bypassing  DC/OS Universe and distributing your DC/OS package directly to your customers. Your customers can add your DC/OS package to their DC/OS cluster, then install it with the same 1-click experience.

* __Can I build a proprietary DC/OS packages with the DC/OS SDK?__

  Yes, you can create proprietary DC/OS packages with the SDK. The SDK is licensed under the Apache License 2.0, which permits derivative works. While many SDK users do choose to share their source code with the community under permissive licensing, you are under no obligation to do so.
  
* __How does the DC/OS SDK compare to Fenzo?__

  [Fenzo](https://github.com/netflix/fenzo), developed by Netflix, is a scheduler Java library for Apache Mesos frameworks that supports plugins for scheduling optimizations and facilitates cluster autoscaling. The DC/OS SDK was developed by Mesosphere to support migrating existing services to DC/OS with a particular emphasis on stateful workloads like databases, messaging systems, and distributed file systems. There are plans to expand the DC/OS SDK to support advanced workloads and use cases enabled by Fenzo. Mesosphere and Netflix hope to unify these libraries in the future.

* __Can I contribute to the DC/OS SDK?__

  Yes, contributions to the SDK are welcome! See [Contributing](https://gist.github.com/keithchambers/3e848d52c94d2e26b7374c9140195bb4).

* __Will the DC/OS SDK work with Apache Mesos (without DC/OS)?__

  Yes, the DC/OS SDK can be used with Apache Mesos. DC/OS and Mesosphere Enterprise DC/OS offer APIs in additional to those available in Apache Mesos. These APIs are only available on DC/OS and Enterprise DC/OS. At this time, Mesosphere only tests the SDK libraries and reference implementations with open source DC/OS and Enterprise DC/OS. Mesosphere may add add test coverage for Apache Mesos in the future, depending on demand.
