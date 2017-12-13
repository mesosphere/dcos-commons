---
post_title: Release Notes
menu_order: 120
enterprise: 'no'
---

## Version 3.0.0-1.0.0-beta

## NOTICE

This is a beta release of the DC/OS Kafka framework. It contains multiple improvements as well as new features that are to be considered of beta quality. Do _not_ operate this version in production.

### New features
- Support for the automated provisioning of TLS artifacts to secure Kafka communication.
- Support for Kerberos and SSL authorization and authentication.
- Support for `Zone` placement constraints in DC/OS 1.11 (beta versions of DC/OS 1.11 coming soon).

### Updates
- Major improvements to the stability and performance of service orchestration.
- The service now uses Kafka v1.0.0. Note that the broker protocol version defaults to 1.0.0, but can be manually set to 1.0.0 if desired.

## Version 1.1.27-0.11.0-beta
### Improvements
- Support for Kafka's graceful shutdown.
- Update to 0.11.0.0 version of Apache Kafka.
- Default broker protocol and log message formats now default to 0.11.0.0.
- Upgrade to [dcos-commons 0.30.0](https://github.com/mesosphere/dcos-commons/releases/tag/0.30.0).

### Bug Fixes
- Numerous fixes and enhancements to service reliability.

## Version 1.1.26-0.10.1.0-beta
### New Features
- Installation in folders is supported
- Use of a CNI network is supported

### Improvements
- Upgrade to [dcos-commons 0.20.1](https://github.com/mesosphere/dcos-commons/releases/tag/0.20.1)
- Default user is now `nobody`
- Allow configuration of scheduler log level
- Readiness check has been added
- Custom ZK configuration is supported
- Statsd is enabled
