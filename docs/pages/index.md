---
layout: gh-basic
title: DC/OS Service SDK
---

__DC/OS SDK__ is a collection of tools, libraries, and documentation for easy integration and automation of stateful services, such as databases, message brokers, and caching services with [DC/OS](https://dcos.io/).

![Status](https://img.shields.io/badge/Status-Alpha-BF97F0.svg?style=flat-square)

DC/OS SDK is currently in alpha stage: it can run services, but APIs change regularly, and features are under active development.
 
### Benefits

* __Simple and Flexible__: The SDK provides the simplicity of a declarative YAML API as well as the flexibility to use the full Java programming language.

* __Automate Maintenance__: Stateful services need to be maintained. With the SDK, you can automate maintenance routines, such as backup and restore, to simplify operations.

* __Available and Durable__: When servers fail, you need to reschedule the tasks without data loss or performance impact. With the SDK, you develop automated recovery strategies so services heal themselves.

* __Production-Proven__: Building reliable services is hard. Uber and Bing platform teams use the SDK for mission-critical databases and message brokers.