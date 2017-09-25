---
post_title: Release Notes
menu_order: 120
enterprise: 'no'
---

# Version  2.0.1-2.6.0-cdh5.11.0

## Bug Fixes
* Tasks will correctly bind on DC/OS 1.10

## Improvements
* Upgraded to [dcos-commons 0.30.1](https://github.com/mesosphere/dcos-commons/releases/tag/0.30.1)

## Documentation
* Updated post-install links for package
* Updated formatting of command line examples
* Updated limitations.md
* Ensured previous version-policy.md content is present

# Version 2.0.0-2.6.0-cdh5.11.0

## Improvements
- Based on the latest stable release of the dcos-commons SDK, which provides numerous benefits:
  - Integration with DC/OS features such as virtual networking and integration with DC/OS access controls.
  - Orchestrated software and configuration update, enforcement of version upgrade paths, and ability to pause/resume updates.
  - Placement constraints for pods.
  - Uniform user experience across a variety of services.
- Update to 2.6.0-cdh5.11.0 version of the Cloudera distribution of Apache HDFS.
- Support replacement and recovery of journal and name nodes.

## Breaking Changes
- This is a major release.  You cannot upgrade to 2.0.0-2.6.0-cdh5.11.0 from a 1.0.x version of the package.  To upgrade, you must perform a fresh install and restore from a backup.
