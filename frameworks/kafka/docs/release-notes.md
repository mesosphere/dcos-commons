---
post_title: Release Notes
menu_order: 120
enterprise: 'no'
---

# Version 2.0.1-0.11.0

## Bug Fixes
* Tasks will correctly bind on DC/OS 1.10
* Disabled TLS
* Fixed `test_availability` integration test

## Improvements
* Upgraded to [dcos-commons 0.30.1](https://github.com/mesosphere/dcos-commons/releases/tag/0.30.1)

## Documentation
* Updated post-install links for package
* Fixed formatting of code blocks, fixed example commands
* Updated limitations.md
* Ensured previous version-policy.md content is present

# Version 2.0.0-0.11.0

## Improvements
- Based on the latest stable release of the dcos-commons SDK, which provides numerous benefits:
  - Integration with DC/OS features such as virtual networking and integration with DC/OS access controls.
  - Orchestrated software and configuration update, enforcement of version upgrade paths, and ability to pause/resume updates.
  - Placement constraints for pods.
  - Uniform user experience across a variety of services.
- Graceful shutdown for brokers.
- Update to 0.11.0.0 version of Apache Kafka (including log and protocol versions).

## Breaking Changes
- This is a major release.  You cannot upgrade to version 2.0.0-0.11.0 from a 1.0.x version of the package. To upgrade, you must perform a fresh install and replicate data across clusters.
