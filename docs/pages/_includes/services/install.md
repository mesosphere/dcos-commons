The default DC/OS {{ include.techName }} installation provides reasonable defaults for trying out the service, but may not be sufficient for production use. You may require a different configuration depending on the context of the deployment.

## Prerequisites

- Depending on your security mode in Enterprise DC/OS, you may [need to provision a service account]({{ include.serviceAccountInstructionsUrl }}) before installing. Only someone with `superuser` permission can create the service account.
	- `strict` [security mode](https://docs.mesosphere.com/latest/installing/custom/configuration-parameters/#security) requires a service account.
	- `permissive` security mode a service account is optional.
	- `disabled` security mode does not require a service account.
- Your cluster must have at least {{ include.minNodeCount }} private nodes.
{{ #include.agentRequirements }}
- {{ include.agentRequirements }}
{{ /include.agentRequirements }}

## Default Installation

To start a basic test cluster {{ include.defaultInstallDescription }}, run the following command on the DC/OS CLI. Enterprise DC/OS users must follow additional instructions. [More information about installing {{ include.techName }} on Enterprise DC/OS]({{ include.enterpriseInstallUrl }}).

```bash
$ dcos package install {{ include.packageName }}
```

This command creates a new instance with the default name `{{ include.serviceName }}`. Two instances cannot share the same name, so installing additional instances beyond the default instance requires [customizing the `name` at install time](#custom-installation) for each additional instance.

All `dcos {{ include.packageName }}` CLI commands have a `--name` argument allowing the user to specify which instance to query. If you do not specify a service name, the CLI assumes a default value matching the package name, i.e. `{{ include.packageName }}`. The default value for `--name` can be customized via the DC/OS CLI configuration:

```bash
$ dcos {{ include.packageName }} --name={{ include.serviceName }} <cmd>
```

**Note:** Alternatively, you can [install from the DC/OS web interface](https://docs.mesosphere.com/latest/deploying-services/install/). If you install from the web interface, you must install the DC/OS CLI subcommands separately. From the DC/OS CLI, enter:

```bash
dcos package install {{ include.packageName }} --cli
```

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

## Regions and Zones

Placement constraints can be applied to zones by referring to the `@zone` key. For example, one could spread pods across a minimum of 3 different zones by specifying the constraint:
```
[["@zone", "GROUP_BY", "3"]]
```

When the region awareness feature is enabled (currently in beta), the `@region` key can also be referenced for defining placement constraints. Any placement constraints that do not reference the `@region` key are constrained to the local region.


### Example

Suppose we have a Mesos cluster with zones `a`,`b`,`c`. For balanced Placement for a Single Region:

```
{
  ...
  "count": 6,
  "placement": "[[\"@zone\", \"GROUP_BY\", \"3\"]]"
}
```

Instances will all be evenly divided between zones `a`,`b`,`c`.
