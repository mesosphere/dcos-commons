---
layout: layout.pug
navigationTitle:
excerpt:
title: Release Notes
menuWeight: 120
---

## Version 2.1.2-3.0.15-beta

## NOTICE

This is a beta release of the DC/OS Apache Cassandra service. It contains multiple improvements as well as new features that are to be considered of beta quality. Do _not_ operate this version in production.

### Improvements
- Remove the dependency on a docker image and allow the service to run as nobody in CentOS distros. ([#2067](https://github.com/mesosphere/dcos-commons/pull/2067))
- Moved scheduler health-checks from HTTP to ([Mesos-level health checks](https://mesosphere.github.io/marathon/docs/health-checks.html#mesos-level-health-checks)).
- Bump JRE to 1.8u162.  ([#2135](https://github.com/mesosphere/dcos-commons/pull/2135))
- Improvements to the pod pause feature. ([#1989](https://github.com/mesosphere/dcos-commons/pull/1989))
- Improve handling of unexpected task statuses. ([#2087](https://github.com/mesosphere/dcos-commons/pull/2087))

### Documentation
- Added limitation section for backup/restore with authentication enabled. ([#2125](https://github.com/mesosphere/dcos-commons/pull/2125))
- Added documentation for placement constraints referencing zones. ([#1906](https://github.com/mesosphere/dcos-commons/pull/1906))

## Version 2.1.1-3.0.15-beta

### NOTICE
This is a beta release of the DC/OS Apache Cassandra framework. It contains multiple improvements as well as new features that are to be considered of beta quality. Do _not_ operate this version in production.

### Updates
- The Cassandra tasks no longer run inside a docker container. This removes the requirement that on Centos based distributions the service must be run as root.

### Bug Fixes
- Scheduler health check now passes during service uninstall.
- Fixed a regression in replacing failed pods on failed agents.
- Replacing a pod on a failed agent now no longer waits for Mesos to register the agent as lost.

## Version 3.0.0-3.0.15-beta

### NOTICE
This is a beta release of the DC/OS Cassandra framework. It contains multiple improvements as well as new features that are to be considered of beta quality. Do _not_ operate this version in production.

### New features
- Support for the automated provisioning of TLS artifacts to secure Cassandra communication.
- Automatic configuration of the system tables on initial deployment.
- Support for `Zone` placement constraints in DC/OS 1.11 (beta versions of DC/OS 1.11 coming soon).

### Updates
- Major improvements to the stability and performance of service orchestration.
- The service now uses Cassandra v3.0.15.

## Version 1.0.32-3.0.14-beta

### Improvements
- Documentation updates to production guidelines.
- Update Cassandra to 3.0.14.
- Upgrade to [dcos-commons 0.30.0](https://github.com/mesosphere/dcos-commons/releases/tag/0.30.0).

### Bug Fixes
- Numerous fixes and enhancements to service reliability.

## Version 1.0.31-3.0.13-beta

### New Features
- Installation in folders is supported
- Use of a CNI network is supported

### Improvements
- Upgrade to [dcos-commons 0.20.1](https://github.com/mesosphere/dcos-commons/releases/tag/0.20.1)
- Default user is now `nobody`
- Allow configuration of scheduler log level
- Automate seed node replacement

### Bug Fixes
- Datacenter configuration is correctly supported

### Upgrade
- This version may only be upgraded from `1.0.30-3.0.13-beta`.  This is a one way upgrade.  Downgrading is not supported.  Interruption or partial upgrade is also not supported.  Upgrade must proceed to completion to ensure availability and retention of data.

## Version 1.0.30-3.0.13-beta

### Bug Fixes
* Fix airgapped cluster support in Cassandra (C* docker image & tar.gz) #1170

### 0.19.2
* CLI commands relating to service update/upgrade #1084
* Log an error if a service requiring secrets is used on a DC/OS cluster which doesn't support them #1180
* Fixes to spec handling for later upgrade compatibility #1164

### 0.19.1
* Workaround for DC/OS strict mode clusters, which currently don't support multi-role #1154

### 0.19.0
* Large offer evaluation refactor and follow-up fixes #1097 #1124
* Initial Resource Refinement support #1114 #1139
* Fixed backwards compatibility around PortsSpec vs PortSpec: Older installs using PortsSpec will be automatically converted to PortSpec #1122
* Treat COMPLETE + STARTING as IN_PROGRESS, not STARTING #1095
* Store Task IPs in ZK properties #1089
* RangeAlgorithms -> RangeUtils #1123
* Small improvements to offer logging #1128
* IP fetch support in bootstrap #1127
