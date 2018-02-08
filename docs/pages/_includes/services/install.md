The default DC/OS {{ include.techName }} installation provides reasonable defaults for trying out the service, but may not be sufficient for production use. You may require a different configuration depending on the context of the deployment.

## Prerequisites

- Depending on your security mode in Enterprise DC/OS, you may [need to provision a service account]({{ include.serviceAccountInstructionsUrl }}) before installing. Only someone with `superuser` permission can create the service account.
	- `strict` [security mode](https://docs.mesosphere.com/latest/installing/custom/configuration-parameters/#security) requires a service account.
	- `permissive` security mode a service account is optional.
	- `disabled` security mode does not require a service account.
- Your cluster must have at least {{ include.minNodeCount }} private nodes.
{{ include.customInstallRequirements }}

## Default Installation

To start a basic test cluster {{ include.defaultInstallDescription }}, run the following command on the DC/OS CLI. Enterprise DC/OS users may need to follow [additional instructions]({{ include.serviceAccountInstructionsUrl }}), depending on the security mode of the Enterprise DC/OS cluster.

```bash
$ dcos package install {{ include.packageName }}
```

This command creates a new instance with the default name `{{ include.serviceName }}`. Two instances cannot share the same name, so installing additional instances beyond the default instance requires [customizing the `name` at install time](#custom-installation) for each additional instance.

All `dcos {{ include.packageName }}` CLI commands have a `--name` argument allowing the user to specify which instance to query. If you do not specify a service name, the CLI assumes a default value matching the package name, i.e. `{{ include.packageName }}`. The default value for `--name` can be customized via the DC/OS CLI configuration:

```bash
$ dcos {{ include.packageName }} --name={{ include.serviceName }} <cmd>
```

**Note:** Alternatively, you can [install from the DC/OS web interface](https://docs.mesosphere.com/latest/deploying-services/install/). If you install {{ include.techName }} from the DC/OS web interface, the `dcos {{ include.packageName }}` CLI commands are not automatically installed to your workstation. They may be manually installed using the DC/OS CLI:

```bash
dcos package install {{ include.packageName }} --cli
```

## Alternate install configurations

The following are some examples of how to customize the installation of your {{ include.techName }} instance.

In each case, you would create a new {{ instance.techName }} instance using the custom configuration as follows:

```bash
$ dcos package install {{ include.packageName }} --options=sample-{{ include.serviceName }}.json
```

**Recommendation:** Store your custom configuration in source control.

### Installing multiple instances

By default, the {{ include.techName }} service is installed with a service name of `{{ include.serviceName }}`. You may specify a different name using a custom service configuration as follows:

```json
{
  "service": {
    "name": "{{ include.serviceName }}-other"
  }
}
```

When the above JSON configuration is passed to the `package install {{ include.packageName }}` command via the `--options` argument, the new service will use the name specified in that JSON configuration:

```bash
$ dcos package install {{ include.packageName }} --options={{ include.serviceName }}-other.json
```

Multiple instances of {{ install.techName }} may be installed into your DC/OS cluster by customizing the name of each instance. For example, you might have one instance of {{ install.techName }} named `{{ include.serviceName }}-staging` and another named `{{ include.serviceName }}-prod`, each with its own custom configuration.

After specifying a custom name for your instance, it can be reached using `dcos {{ include.packageName }}` CLI commands or directly over HTTP as described [below](#addressing-named-instances).

**Note:** The service name _cannot_ be changed after initial install. Changing the service name would require installing a new instance of the service against the new name, then copying over any data as necessary to the new instance.

### Installing into folders

In DC/OS 1.10 and above, services may be installed into _folders_ by specifying a slash-delimited service name. For example:

```json
{
  "service": {
    "name": "/foldered/path/to/{{ include.serviceName }}"
  }
}
```

The above example will install the service under a path of `foldered` => `path` => `to` => `{{ include.serviceName }}`. It can then be reached using `dcos {{ include.packageName }}` CLI commands or directly over HTTP as described [below](#addressing-named-instances).

**Note:** The service folder location _cannot_ be changed after initial install. Changing the service location would require installing a new instance of the service against the new location, then copying over any data as necessary to the new instance.

### Addressing named instances

After you've installed the service under a custom name or under a folder, it may be accessed from all `dcos {{ package.packageName }}` CLI commands using the `--name` argument. By default, the `--name` value defaults to the name of the package, or `{{ include.packageName }}`.

For example, if you had an instance named `{{ include.serviceName }}-dev`, the following command would invoke a `pod list` command against it:

```bash
$ dcos {{ include.packageName }} --name={{ include.serviceName }}-dev pod list
```

The same query would be over HTTP as follows:

```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.serviceName }}-dev/v1/pod
```

Likewise, if you had an instance in a folder like `/foldered/path/to/{{ include.serviceName }}`, the following command would invoke a `pod list` command against it:

```bash
$ dcos {{ include.packageName }} --name=/foldered/path/to/{{ include.serviceName }} pod list
```

Similarly, it could be queried directly over HTTP as follows:

```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/foldered/path/to/{{ include.serviceName }}-dev/v1/pod
```

**Note:** You may add a `-v` (verbose) argument to any `dcos {{ include.packageName }}` command to see the underlying HTTP queries that are being made. This can be a useful tool to see where the CLI is getting its information. In practice, `dcos {{ include.packageName }}` commands are a thin wrapper around an HTTP interface provided by the DC/OS {{ include.techName }} Service itself.

### Virtual networks

DC/OS {{ include.techName }} supports deployment on virtual networks on DC/OS (including the `dcos` overlay network), allowing each container (task) to have its own IP address and not use port resources on the agent machines. This can be specified by passing the following configuration during installation:

```json
{
  "service": {
    "virtual_network_enabled": true
  }
}
```

**Note:** Once the service is deployed on a virtual network, it cannot be updated to use the host network.

{{ include.customInstallConfigurations }}

## Integration with DC/OS access controls

In Enterprise DC/OS 1.10 and above, you can integrate your SDK-based service with DC/OS ACLs to grant users and groups access to only certain services. You do this by installing your service into a folder, and then restricting access to some number of folders. Folders also allow you to namespace services. For instance, `staging/{{ include.serviceName }}` and `production/{{ include.serviceName }}`.

Steps:

1. In the DC/OS GUI, create a group, then add a user to the group. Or, just create a user. Click **Organization** > **Groups** > **+** or **Organization** > **Users** > **+**. If you create a group, you must also create a user and add them to the group.
1. Give the user permissions for the folder where you will install your service. In this example, we are creating a user called `developer`, who will have access to the `/testing` folder.

     Select the group or user you created. Select **ADD PERMISSION** and then toggle to **INSERT PERMISSION STRING**. Add each of the following permissions to your user or group, and then click **ADD PERMISSIONS**.

     ```
     dcos:adminrouter:service:marathon full
     dcos:service:marathon:marathon:services:/testing full
     dcos:adminrouter:ops:mesos full
     dcos:adminrouter:ops:slave full
     ```

1. Install your service into a folder called `test`. Go to **Catalog**, then search for **{{ include.packageName }}**.
1. Click **CONFIGURE** and change the service name to `/testing/{{ include.serviceName }}`, then deploy.

     The slashes in your service name are interpreted as folders. You are deploying `{{ include.serviceName }}` in the `/testing` folder. Any user with access to the `/testing` folder will have access to the service.

**Important:**
- Services cannot be renamed. Because the location of the service is specified in the name, you cannot move services between folders.
- DC/OS 1.9 and earlier does not accept slashes in service names. You may be able to create the service, but you will encounter unexpected problems.

## Interacting with your foldered service

- Interact with your foldered service via the DC/OS CLI with this flag: `--name=/path/to/myservice`.
- To interact with your foldered service over the web directly, use `http://<dcos-url>/service/path/to/myservice`. E.g., `http://<dcos-url>/service/testing/{{ include.serviceName }}/v1/endpoints`.

## Placement Constraints

Placement constraints allow you to customize where a service is deployed in the DC/OS cluster. Depending on the service, some or all components may be configurable using [Marathon operators (reference)](http://mesosphere.github.io/marathon/docs/constraints.html). For example, `[["hostname", "UNIQUE"]]` ensures that at most one pod instance is deployed per agent.

A common task is to specify a list of whitelisted systems to deploy to. To achieve this, use the following syntax for the placement constraint:
```
[["hostname", "LIKE", "10.0.0.159|10.0.1.202|10.0.3.3"]]
```

You must include spare capacity in this list, so that if one of the whitelisted systems goes down, there is still enough room to repair your service (via [`pod replace`](#replace-a-pod)) without requiring that system.

### Regions and Zones

Placement constraints can be applied to zones by referring to the `@zone` key. For example, one could spread pods across a minimum of 3 different zones by specifying the constraint:
```
[["@zone", "GROUP_BY", "3"]]
```

When the region awareness feature is enabled (currently in beta), the `@region` key can also be referenced for defining placement constraints. Any placement constraints that do not reference the `@region` key are constrained to the local region.

#### Example

Suppose we have a Mesos cluster with three zones. For balanced placement across those three zones, we would have a configuration like this:

```
{
  ...
  "count": 6,
  "placement": "[[\"@zone\", \"GROUP_BY\", \"3\"]]"
}
```

Instances will all be evenly divided between those three zones.
