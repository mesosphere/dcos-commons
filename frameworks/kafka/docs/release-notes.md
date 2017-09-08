---
post_title: Release Notes
menu_order: 40
enterprise: 'no'
---

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
