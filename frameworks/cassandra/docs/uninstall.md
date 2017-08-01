---
post_title: Uninstalling
nav_title: Uninstalling
menu_order: 60
post_excerpt: ""
feature_maturity: preview
enterprise: 'no'
---

Follow these steps to uninstall the service.

1. Uninstall the service. From the DC/OS CLI, enter `dcos package uninstall`.
1. Clean up remaining reserved resources with the framework cleaner script, `janitor.py`. [More information about the framework cleaner script](https://docs.mesosphere.com/1.9/deploying-services/uninstall/#framework-cleaner).

To uninstall an instance named `cassandra` (the default), run:
```
MY_SERVICE_NAME=cassandra
dcos package uninstall --app-id=$MY_SERVICE_NAME beta-cassandra
dcos node ssh --master-proxy --leader "docker run mesosphere/janitor /janitor.py \
    -r $MY_SERVICE_NAME-role \
    -p $MY_SERVICE_NAME-principal \
    -z dcos-service-$MY_SERVICE_NAME"
```
