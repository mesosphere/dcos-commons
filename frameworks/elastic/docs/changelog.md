---
post_title: Changelog
menu_order: 60
feature_maturity: preview
enterprise: 'no'
---

# Changelog

## elastic-1.0.11-5.4.0-beta

### Breaking Changes

- Kibana has been removed, along with the proxylite helper service. 

### Improvements/Features

- Added an option to toggle installation of commercial X-Pack plugin (disabled by default).
- Increased ingest node default RAM to 2GB [(issue: #908)](https://github.com/mesosphere/dcos-commons/issues/908).
- Added a configurable health check user/password to use as Elastic credentials during readiness/health checks.

### Upgrades

- Upgraded to Elastic 5.4.0.
- Upgraded to Support Diagnostics Version 5.12.
- Upgraded to dcos-commons-0.16.0.