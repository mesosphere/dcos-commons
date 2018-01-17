---
layout: layout.pug
navigationTitle: 
menuWeight: 0
excerpt:
post_title: Release Notes
menu_order: 120
enterprise: 'no'
---

# Version 2.0.4-1.0.0

## Updates
- Upgraded to Kafka v1.0.0. **Note:** Protocol and log version defaults are set to 0.11.0. After upgrading to this version, they may be set to 1.0.0.

# Version 2.0.3-0.11.0

### Bug Fixes
* Uninstall now handles failed tasks correctly.
* Fixed a timing issue in the broker readiness check that caused brokers to be stuck in STARTING when the service is allocated more than 2 CPUs per broker.

# Version 2.0.2-0.11.0

### Bug Fixes

- Dynamic ports are no longer sticky across pod replaces
- Further fixes to scheduler behavior during task status transitions.

#### Improvements

- Updated JRE version to 8u144.
- Improved handling of error codes in service CLI.

# Version 2.0.1-0.11.0

### Bug Fixes
* Tasks will correctly bind on DC/OS 1.10.

### Documentation
* Updated post-install links for package.
* Updated `limitations.md`.
* Ensured previous `version-policy.md` content is present.

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
