<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

Support for an improved uninstall process was introduced in DC/OS 1.10. In order to take advantage of this new support, changes were required to the `{{ include.data.packageName }}` package, which were included as of versions 2.0.0-x and greater.

### DC/OS 1.10 and newer, and package 2.0.0-x and newer

If you are using DC/OS 1.10 or newer and the installed service has a version greater than 2.0.0-x:

1. Uninstall the service. From the DC/OS CLI, enter `dcos package uninstall --app-id=<instancename> {{ include.data.packageName }}`.

For example, to uninstall the {{ include.data.techName }} instance named `{{ include.data.serviceName }}-dev`, run:

```bash
dcos package uninstall --app-id={{ include.data.serviceName }}-dev {{ include.data.packageName }}
```

### DC/OS 1.9 and older, or package older than 2.0.0-x

If you are running DC/OS 1.9 or older, or a version of the service that is older than 2.0.0-x, follow these steps:

1. Stop the service. From the DC/OS CLI, enter `dcos package uninstall --app-id=<instancename> <packagename>`.
   For example, `dcos package uninstall --app-id={{ include.data.serviceName }}-dev {{ include.data.packageName }}`.
1. Clean up remaining reserved resources with the framework cleaner script, `janitor.py`. See [DC/OS documentation](https://docs.mesosphere.com/1.9/deploying-services/uninstall/#framework-cleaner) for more information about the framework cleaner script.

For example, to uninstall {{ include.data.techName }} instance named `{{ include.data.serviceName }}-dev`, run:

```bash
$ MY_SERVICE_NAME={{ include.data.serviceName }}-dev
$ dcos package uninstall --app-id=$MY_SERVICE_NAME {{ include.data.packageName }}`.
$ dcos node ssh --master-proxy --leader "docker run mesosphere/janitor /janitor.py \
    -r $MY_SERVICE_NAME-role \
    -p $MY_SERVICE_NAME-principal \
    -z dcos-service-$MY_SERVICE_NAME"
```

<!-- END DUPLICATE BLOCK -->
