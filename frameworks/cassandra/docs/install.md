---
post_title: Installing and Customizing
nav_title: Installing and Customizing
menu_order: 20
post_excerpt: ""
feature_maturity: preview
enterprise: 'no'
---

The default DC/OS Apache Cassandra installation provides reasonable defaults for trying out the service, but may not be sufficient for production use. You may require different configurations depending on the context of the deployment.

## Prerequisities
 - If you are using Enterprise DC/OS, you may [need to provision a service account](https://docs.mesosphere.com/1.9/security/service-auth/custom-service-auth/) before installing DC/OS Apache Cassandra. Only someone with `superuser` permission can create the service account.
 - `strict` [security mode](https://docs.mesosphere.com/1.9/administration/installing/custom/configuration-parameters/#security) requires a service account.
 - In `permissive` security mode a service account is optional.
 - `disabled` security mode does not require a service account.
 - Your cluster must have at least 3 private nodes.

## Installation from the DC/OS CLI

To start a basic test cluster, run the following command on the DC/OS CLI. Enterprise DC/OS users must follow additional instructions. [More information about installing DC/OS Apache Cassandra on Enterprise DC/OS](https://docs.mesosphere.com/1.9/security/service-auth/custom-service-auth/).

```shell
dcos package install beta-cassandra
```
You can specify a custom configuration in an `options.json` file and pass it to `dcos package install` using the `--options` parameter.

```
$ dcos package install beta-cassandra --options=<options>.json
```

For more information about building the `options.json` file, see the [DC/OS documentation](https://docs.mesosphere.com/latest/usage/managing-services/config-universe-service/) for service configuration access.

## Installation from the DC/OS Web Interface

You can [install DC/OS Apache Cassandra from the DC/OS web interface](https://docs.mesosphere.com/1.9/usage/managing-services/install/). If you install DC/OS Apache Cassandra from the web interface, you must install the DC/OS Apache Cassandra CLI subcommands separately. From the DC/OS CLI, enter:
```bash
dcos package install beta-cassandra --cli
```
Choose `ADVANCED INSTALLATION` to perform a custom installation.
