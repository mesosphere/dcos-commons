---
post_title: Release Notes
menu_order: 120
enterprise: 'no'
---

# Version 2.0.2-3.0.14

### Bug Fixes

* Further fixes to scheduler behavior during task status transitions.

### Improvements

* Updated JRE version to 8u144.
* Improved handling of error codes in service CLI.


# Version 2.0.1-3.0.14

## Bug Fixes
* Corrected closing brace in Cassandra mustache.
* Fixed restore-snapshot port rendering.
* Tasks will correctly bind on DC/OS 1.10.
* Fixed config generation.

### Documentation
* Updated post-install links for package.
* Updated `limitations.md`.
* Ensured previous `version-policy.md` content is present.
* Updated service user section

# Version 2.0.0-3.0.14

## Improvements
- Based on the latest stable release of the dcos-commons SDK, which provides numerous benefits:
  - Integration with DC/OS features such as virtual networking and integration with DC/OS access controls.
  - Orchestrated software and configuration update, enforcement of version upgrade paths, and ability to pause/resume updates.
  - Placement constraints for pods.
  - Uniform user experience across a variety of services.
- Upgrade to version 3.0.14 of Apache Cassandra.

## Breaking Changes
- This is a major release.  You cannot upgrade to 2.0.0-3.0.14 from a 1.0.x version of the package.  To upgrade, you must perform a fresh install and restore from a backup.
