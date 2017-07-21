---
title: Frequently Asked Questions
menu_order: 3
---

#### __What is the DC/OS SDK?__

  The DC/OS SDK is a collection of tools, libraries, and documentation for integrating stateful services with DC/OS.

#### __When should I use the DC/OS SDK?__

  Independent software vendors (ISVs) interested in offering production-grade stateful services for DC/OS should use the SDK.

#### __How can I get commercial support for the DC/OS SDK?__

  Mesosphere offers support to approved ISVs as part of the Mesosphere Partner Program.

#### __What is a DC/OS service?__

  A [DC/OS service](https://dcos.io/docs/latest/overview/concepts/#dcos-service) is a service (typically a stateful, distributed service) in a DC/OS cluster. These services often have multiple instances that run on discrete DC/OS agents and provide redundancy and high availability. DC/OS services built using the DC/OS SDK implement schedulers, which allow for orchestrated installation, update and backup/recovery.

#### __What is a DC/OS package?__

  A DC/OS package is an artifact for installing a service to any DC/OS cluster. DC/OS packages are analogous in concept to other operation systems packages like RPMs and DEBs. A DC/OS package includes metadata such as name, version, configurable settings, and service executables.

#### __What is a Mesos framework?__

  In Mesos, a framework is the component responsible for the second level of scheduling in the [Apache Mesos two-level scheduler architecture](http://mesos.apache.org/documentation/latest/architecture/). Strictly speaking, the SDK is used to build Mesos frameworks, which appear as “services” in DC/OS.

#### __What are the advantages of using the DC/OS SDK vs. building a framework from scratch?__

  The SDK dramatically simplifies integrating stateful services with DC/OS and Mesosphere Enterprise DC/OS so that you can focus on your goals. In the past, integrating existing services with DC/OS meant developing a scheduler to evaluate and accept resource offers, launch and monitor tasks, and handle task reconciliation. Stateful services introduce additional concerns like reserving and accounting for resources, such as persistent volumes, and more complicated failure recovery semantics. Prior to the SDK, integrating stateful services with DC/OS required tens of thousands of lines of code and months of development and maintenance. With the SDK, even the most complex stateful services require only a few hundred lines of code to integrate with DC/OS.

#### __Do I need to be a Java developer to use the DC/OS SDK?__

  No, the SDK offers a YAML interface suitable for simple integrations. Basic Java development skills are necessary for more complex integrations.

#### __How long will it take to build my DC/OS service?__

  A developer with basic Java experience will find it easy to use the SDK to integrate existing stateful services with DC/OS. A basic integration generally takes 1-3 days to develop, test, and release. Advanced integrations that include custom maintenance plans and automated recovery strategies usually take around 2-3 weeks to develop, test, and release.

#### __How can I distribute my DC/OS package?__

  You have can distribute your DC/OS package in several ways:

  When you want to maximize reach, [DC/OS Universe](https://github.com/mesosphere/Universe) is the recommended distribution channel. DC/OS Universe is an online repository of DC/OS packages available to all community and commercial users. Users install packages with a few clicks from Universe.

  When you need to control distribution, we recommend bypassing DC/OS Universe and distributing your DC/OS package directly to your users. Your users can add your DC/OS package to their DC/OS cluster, then install your package with a few clicks as usual.

#### __Can I use the DC/OS SDK to build a proprietary DC/OS package?__

  Yes, you can use the SDK to build a proprietary package. The SDK is licensed under the Apache License 2.0, which permits proprietary derivative works.

#### __Can I contribute to the DC/OS SDK?__

  Yes, contributions are welcome. See [CONTRIBUTING](../../CONTRIBUTING.md).

#### __Will the DC/OS SDK work with Apache Mesos (without DC/OS)?__

  Yes, the DC/OS SDK can be used with Apache Mesos. DC/OS and Mesosphere Enterprise DC/OS offer APIs in additional to those available in Apache Mesos. These APIs are only available on DC/OS and Enterprise DC/OS. At this time, Mesosphere only tests the SDK libraries with open source DC/OS and Enterprise DC/OS. The reference implementations may depend on DC/OS APIs.
