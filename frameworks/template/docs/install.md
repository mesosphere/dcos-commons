---
post_title: Install and Customize
menu_order: 20
enterprise: 'no'
---

The default DC/OS _SERVICENAME_ Service installation provides reasonable defaults for trying out the service, but may not be sufficient for production use. You may require different configurations depeneding on the context of the deployment.

## Prerequisites

- If you are using Enterprise DC/OS, you may [need to provision a service account](https://docs.mesosphere.com/latest/security/service-auth/custom-service-auth/) before installing DC/OS _SERVICENAME_ Service. Only someone with `superuser` permission can create the service account.
  - `strict` [security mode](https://docs.mesosphere.com/latest/administration/installing/custom/configuration-parameters/#security) requires a service account.
  - In `permissive` security mode a service account is optional.
  - `disabled` security mode does not require a service account.
- Your cluster must have at least _NUMBER_ private nodes.

# Installing from the DC/OS CLI

To start a basic test cluster of _SERVICENAME_, run the following command on the DC/OS CLI. Enterprise DC/OS users must follow additional instructions. [More information about installing _SERVICENAME_ on Enterprise DC/OS](https://docs.mesosphere.com/latest/security/service-auth/custom-service-auth/).

```shell
dcos package install _PKGNAME_ 
```

You can specify a custom configuration in an `options.json` file and pass it to `dcos package install` using the `--options` parameter.

```shell
dcos package install _PKGNAME_ --options=options.json
```

**Recommendation:** Store your custom configuration in source control.

For more information on building the `options.json` file, see [DC/OS documentation](https://docs.mesosphere.com/latest/usage/managing-services/config-universe-service/) for service configuration access.

## Installng from the DC/OS Web Interface

You can [install _SERVICENAME_ from the DC/OS web interface](https://docs.mesosphere.com/latest/usage/managing-services/install/). If you install _SERVICENAME_ from the web interface, you must install the DC/OS _SERVICENAME_ CLI subcommands separately. From the DC/OS CLI, enter:

```shell
dcos package install _PKGNAME_ --cli
```

Choose `ADVANCED INSTALLATION` to perform a custom installation.
