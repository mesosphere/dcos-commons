---
post_title: Release Notes
menu_order: 120
enterprise: 'no'
---

## Kafka 1.1.27-0.11.0-beta
### Improvements
- Support for Kafka's graceful shutdown.
- Update to 0.11.0.0 version of Apache Kafka.
- Default broker protocol and log message formats now default to 0.11.0.0.
- Upgrade to [dcos-commons 0.30.0](https://github.com/mesosphere/dcos-commons/releases/tag/0.30.0).

### Bug Fixes
- Numerous fixes and enhancements to service reliability.

## Kafka 1.1.26-0.10.1.0-beta
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
