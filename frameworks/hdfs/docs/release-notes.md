---
post_title: Release Notes
menu_order: 120
enterprise: 'no'
---

## Version 2.0.3-2.6.0-cdh5.11.0

### Bug Fixes
* Dashes in envvars replaced with underscores to support Ubuntu.
* Some numeric configuration parameters could be interpreted incorrectly as floats, and are fixed.
* Uninstall now handles failed tasks correctly.

## Version 2.0.0-2.6.0-cdh5.11.0

### Improvements
- Enhanced inter-node checks for journal and name nodes.
- Upgrade to [dcos-commons 0.30.0](https://github.com/mesosphere/dcos-commons/releases/tag/0.30.0).

### Bug Fixes
- Numerous fixes and enhancements to service reliability.

## Version 1.3.3-2.6.0-cdh5.11.0-beta

### New Features
- Installation in folders is supported
- Use of a CNI network is supported

### Improvements
- Upgraded to [dcos-commons 0.20.1](https://github.com/mesosphere/dcos-commons/releases/tag/0.20.1)
- Upgraded to `cdh 5.11.0`
- Default user is now `nobody`
- Allow configuration of scheduler log level
- Added a readiness check to journal nodes

### Documentation
- Pre-install notes include five agent pre-requisite
- Updated CLI documentation
