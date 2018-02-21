The default DC/OS {{ include.data.techName }} installation provides reasonable defaults for trying out the service, but may not be sufficient for production use. You may require a different configuration depending on the context of the deployment.

## Prerequisites

- Depending on your security mode in Enterprise DC/OS, you may [need to provision a service account]({{ include.data.install.serviceAccountInstructionsUrl }}) before installing. Only someone with `superuser` permission can create the service account.
	- `strict` [security mode](https://docs.mesosphere.com/latest/installing/custom/configuration-parameters/#security) requires a service account.
	- `permissive` security mode a service account is optional.
	- `disabled` security mode does not require a service account.
- Your cluster must have at least {{ include.data.install.minNodeCount }} private nodes.
{{ include.data.install.customRequirements }}

## Default Installation

To start a basic test cluster {{ include.data.install.nodeDescription }}, run the following command on the DC/OS CLI. Enterprise DC/OS users may need to follow [additional instructions]({{ include.data.install.serviceAccountInstructionsUrl }}), depending on the security mode of the Enterprise DC/OS cluster.

```bash
$ dcos package install {{ include.data.packageName }}
```

This command creates a new instance with the default name `{{ include.data.serviceName }}`. Two instances cannot share the same name, so installing additional instances beyond the default instance requires [customizing the `name` at install time](#custom-installation) for each additional instance.

All `dcos {{ include.data.packageName }}` CLI commands have a `--name` argument allowing the user to specify which instance to query. If you do not specify a service name, the CLI assumes a default value matching the package name, i.e. `{{ include.data.packageName }}`. The default value for `--name` can be customized via the DC/OS CLI configuration:

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} <cmd>
```

**Note:** Alternatively, you can [install from the DC/OS web interface](https://docs.mesosphere.com/latest/deploying-services/install/). If you install {{ include.data.techName }} from the DC/OS web interface, the `dcos {{ include.data.packageName }}` CLI commands are not automatically installed to your workstation. They may be manually installed using the DC/OS CLI:

```bash
dcos package install {{ include.data.packageName }} --cli
```

## Alternate install configurations

The following are some examples of how to customize the installation of your {{ include.data.techName }} instance.

In each case, you would create a new {{ include.data.techName }} instance using the custom configuration as follows:

```bash
$ dcos package install {{ include.data.packageName }} --options=sample-{{ include.data.serviceName }}.json
```

**Recommendation:** Store your custom configuration in source control.

### Installing multiple instances

By default, the {{ include.data.techName }} service is installed with a service name of `{{ include.data.serviceName }}`. You may specify a different name using a custom service configuration as follows:

```json
{
  "service": {
    "name": "{{ include.data.serviceName }}-other"
  }
}
```

When the above JSON configuration is passed to the `package install {{ include.data.packageName }}` command via the `--options` argument, the new service will use the name specified in that JSON configuration:

```bash
$ dcos package install {{ include.data.packageName }} --options={{ include.data.serviceName }}-other.json
```

Multiple instances of {{ include.data.techName }} may be installed into your DC/OS cluster by customizing the name of each instance. For example, you might have one instance of {{ include.data.techName }} named `{{ include.data.serviceName }}-staging` and another named `{{ include.data.serviceName }}-prod`, each with its own custom configuration.

After specifying a custom name for your instance, it can be reached using `dcos {{ include.data.packageName }}` CLI commands or directly over HTTP as described [below](#addressing-named-instances).

**Note:** The service name _cannot_ be changed after initial install. Changing the service name would require installing a new instance of the service against the new name, then copying over any data as necessary to the new instance.

### Installing into folders

In DC/OS 1.10 and above, services may be installed into _folders_ by specifying a slash-delimited service name. For example:

```json
{
  "service": {
    "name": "/foldered/path/to/{{ include.data.serviceName }}"
  }
}
```

The above example will install the service under a path of `foldered` => `path` => `to` => `{{ include.data.serviceName }}`. It can then be reached using `dcos {{ include.data.packageName }}` CLI commands or directly over HTTP as described [below](#addressing-named-instances).

**Note:** The service folder location _cannot_ be changed after initial install. Changing the service location would require installing a new instance of the service against the new location, then copying over any data as necessary to the new instance.

### Addressing named instances

After you've installed the service under a custom name or under a folder, it may be accessed from all `dcos {{ include.data.packageName }}` CLI commands using the `--name` argument. By default, the `--name` value defaults to the name of the package, or `{{ include.data.packageName }}`.

For example, if you had an instance named `{{ include.data.serviceName }}-dev`, the following command would invoke a `pod list` command against it:

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }}-dev pod list
```

The same query would be over HTTP as follows:

```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}-dev/v1/pod
```

Likewise, if you had an instance in a folder like `/foldered/path/to/{{ include.data.serviceName }}`, the following command would invoke a `pod list` command against it:

```bash
$ dcos {{ include.data.packageName }} --name=/foldered/path/to/{{ include.data.serviceName }} pod list
```

Similarly, it could be queried directly over HTTP as follows:

```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/foldered/path/to/{{ include.data.serviceName }}-dev/v1/pod
```

**Note:** You may add a `-v` (verbose) argument to any `dcos {{ include.data.packageName }}` command to see the underlying HTTP queries that are being made. This can be a useful tool to see where the CLI is getting its information. In practice, `dcos {{ include.data.packageName }}` commands are a thin wrapper around an HTTP interface provided by the DC/OS {{ include.data.techName }} Service itself.

### Virtual networks

DC/OS {{ include.data.techName }} supports deployment on virtual networks on DC/OS (including the `dcos` overlay network), allowing each container (task) to have its own IP address and not use port resources on the agent machines. This can be specified by passing the following configuration during installation:

```json
{
  "service": {
    "virtual_network_enabled": true
  }
}
```

**Note:** Once the service is deployed on a virtual network, it cannot be updated to use the host network.
