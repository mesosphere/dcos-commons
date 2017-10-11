---
post_title: Uninstall
menu_order: 30
enterprise: 'no'
---

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

### DC/OS 1.10

If you are using DC/OS 1.10 and the installed service has a version greater than 2.0.0-x:

1. Uninstall the service. From the DC/OS CLI, enter `dcos package uninstall beta-kafka --app-id=<instancename>`.

For example, to uninstall a Kafka instance named `kafka-dev`, run:

```bash
$ dcos package uninstall beta-kafka --app-id=kafka-dev
```

### Older versions

If you are running DC/OS 1.9 or older, or a version of the service that is older than 2.0.0-x, follow these steps:

1. Stop the service. From the DC/OS CLI, enter `dcos package uninstall --app-id=<instancename> <packagename>`.
   For example, `dcos package uninstall beta-kafka --app-id=kafka-dev`.
1. Clean up remaining reserved resources with the framework cleaner script, `janitor.py`. See [DC/OS documentation](https://docs.mesosphere.com/1.9/deploying-services/uninstall/#framework-cleaner) for more information about the framework cleaner script.

For example, to uninstall a Kafka instance named `kakfa-dev`, run:

```bash
$ MY_SERVICE_NAME=kafka-dev
$ dcos package uninstall beta-kafka --app-id=`
$ dcos node ssh --master-proxy --leader "docker run mesosphere/janitor /janitor.py \
    -r -role \
    -p -principal \
    -z dcos-service-"
```

<!-- END DUPLICATE BLOCK -->
