---
layout: layout.pug
navigationTitle: 
menuWeight: 0
excerpt:
post_title: Uninstall
menu_order: 30
enterprise: 'no'
---

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

### DC/OS 1.10

If you are using DC/OS 1.10 and the installed service has a version greater than 2.0.0-x:

1. Uninstall the service. From the DC/OS CLI, enter `dcos package uninstall --app-id=<instancename> kafka`.

For example, to uninstall a Kafka instance named `kafka-dev`, run:

```bash
$ dcos package uninstall --app-id=kafka-dev kafka
```

### Older versions

If you are running DC/OS 1.9 or older, or a version of the service that is older than 2.0.0-x, follow these steps:

1. Stop the service. From the DC/OS CLI, enter `dcos package uninstall --app-id=<instancename> <packagename>`.
   For example:
   ```bash
   $ dcos package uninstall --app-id=kafka-dev kafka
   ```
1. Clean up remaining reserved resources with the framework cleaner script, `janitor.py`. See [DC/OS documentation](https://docs.mesosphere.com/1.9/deploying-services/uninstall/#framework-cleaner) for more information about the framework cleaner script.

For example, to uninstall a Kafka instance named `kakfa-dev`, run:

```bash
$ MY_SERVICE_NAME=kafka-dev
$ dcos package uninstall --app-id=$MY_SERVICE_NAME kafka
$ dcos node ssh --master-proxy --leader "docker run mesosphere/janitor /janitor.py \
    -r $MY_SERVICE_NAME-role \
    -p $MY_SERVICE_NAME-principal \
    -z dcos-service-$MY_SERVICE_NAME"
```

<!-- END DUPLICATE BLOCK -->
