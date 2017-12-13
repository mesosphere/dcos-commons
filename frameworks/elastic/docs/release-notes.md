---
post_title: Release Notes
menu_order: 120
enterprise: 'no'
---

## Version 3.0.0-5.6.4-beta

## NOTICE

This is a beta release of the DC/OS Elastic framework. It contains multiple improvements as well as new features that are to be considered of beta quality. Do _not_ operate this version in production.

### New features
- Support for the automated provisioning of TLS artifacts to secure Elastic communication (requires X-Pack).
- Support for `Zone` placement constraints in DC/OS 1.11 (beta versions of DC/OS 1.11 coming soon).

### Updates
- Major improvements to the stability and performance of service orchestration.
- The service now uses Elastic v5.6.4.

## Version 2.1.0-5.6.2

### New Features
* Custom configuration can now be passed to Elastic plugins. See [the documentation](custom-elasticsearch-yaml.md).

### Bugs
* Uninstall now handles failed tasks correctly.

## Version 2.0.0-5.5.1

### Improvements
- Default to 0 ingest nodes.
- Automatic management of gateway settings.
- Upgrade to [dcos-commons 0.30.0](https://github.com/mesosphere/dcos-commons/releases/tag/0.30.0).

### Bug Fixes
- Numerous fixes and enhancements to service reliability.

## Version 1.0.15-5.5.1-beta

### Improvements
- Upgrade to [dcos-commons 0.20.1](https://github.com/mesosphere/dcos-commons/releases/tag/0.20.1)
- Upgrade to Elastic 5.5.1

## Version 1.0.14-5.4.1-beta

### New Features
- Installation in folders is supported
- Use of a CNI network is supported

### Improvements
- Upgrade to [dcos-commons 0.20.0](https://github.com/mesosphere/dcos-commons/releases/tag/0.20.0)
- Upgrade to Elastic 5.5.0
- Default user is now `nobody`
- Allow configuration of scheduler log level
- Kibana's cpu and memory are now configurable

### Bug Fixes
- Stop downloading Statsd zip file twice

## Version 1.0.13-5.4.1-beta

### New Features
- Enabled Elastic framework to work in offline/airgapped cluster (#1091)

### Upgrades
- Upgraded to Elasticsearch and Kibana 5.4.1.
- Upgraded to dcos-commons-0.18.0.

## Version 1.0.11-5.4.0-beta

### Breaking Changes

- Kibana has been removed from the Elastic package, along with the proxylite helper service. Please see the '[Connecting Clients](connecting.md)' section for instructions on how to provision and connect Kibana on DC/OS.

### Improvements/Features

- Added an option to toggle installation of commercial X-Pack plugin (disabled by default).
- Increased ingest node default RAM to 2GB [(issue: #908)](https://github.com/mesosphere/dcos-commons/issues/908).
- Added a configurable health check user/password to use as Elastic credentials during readiness/health checks.

### Upgrades

- Upgraded to Elastic 5.4.0.
- Upgraded to Support Diagnostics Version 5.12.
- Upgraded to dcos-commons-0.16.0.
