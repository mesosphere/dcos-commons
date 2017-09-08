---
post_title: Upgrade
menu_order: 130
feature_maturity: preview
enterprise: 'no'
---

# Overview
We support upgrade/rollback between adjacent versions only. Concretely, to upgrade from version 2 to version 4, you must upgrade from 2 -> 3, then from 3 -> 4.

# Upgrade Instructions

1. In the DC/OS web interface, destroy the DC/OS _SERVICENAME_ Service instance to be updated. (This will not kill the node tasks).
2. Verify that you no longer see the DC/OS _SERVICENAME_ Service instance in the DC/OS web interface.
3. From the DC/OS CLI, install the N+1 version of DC/OS _SERVICENAME_ Service (where N is your current version) with any customizations you require in a JSON options file:

```shell
dcos package install _PKGNAME_ --options=/path/to/options.json
```

The command above will trigger the install of the latest DC/OS _SERVICENAME_ Service version. You can follow the upgrade progress by making a REST request identical to the one used to follow the progress of a configuration upgrade. See the Configuring section for more information.

Note: The upgrade process will cause all of your _SERVICENAME_ node processes to restart.
