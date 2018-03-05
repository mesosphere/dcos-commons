## Integration with DC/OS access controls

In Enterprise DC/OS 1.10 and above, you can integrate your SDK-based service with DC/OS ACLs to grant users and groups access to only certain services. You do this by installing your service into a folder, and then restricting access to some number of folders. Folders also allow you to namespace services. For instance, `staging/{{ include.data.serviceName }}` and `production/{{ include.data.serviceName }}`.

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

1. Install your service into a folder called `test`. Go to **Catalog**, then search for **{{ include.data.packageName }}**.
1. Click **CONFIGURE** and change the service name to `/testing/{{ include.data.serviceName }}`, then deploy.

     The slashes in your service name are interpreted as folders. You are deploying `{{ include.data.serviceName }}` in the `/testing` folder. Any user with access to the `/testing` folder will have access to the service.

**Important:**
- Services cannot be renamed. Because the location of the service is specified in the name, you cannot move services between folders.
- DC/OS 1.9 and earlier does not accept slashes in service names. You may be able to create the service, but you will encounter unexpected problems.

## Interacting with your foldered service

- Interact with your foldered service via the DC/OS CLI with this flag: `--name=/path/to/myservice`.
- To interact with your foldered service over the web directly, use `http://<dcos-url>/service/path/to/myservice`. E.g., `http://<dcos-url>/service/testing/{{ include.data.serviceName }}/v1/endpoints`.

## Placement Constraints

Placement constraints allow you to customize where a service is deployed in the DC/OS cluster. Depending on the service, some or all components may be configurable using [Marathon operators (reference)](http://mesosphere.github.io/marathon/docs/constraints.html). For example, `[["hostname", "UNIQUE"]]` ensures that at most one pod instance is deployed per agent.

A common task is to specify a list of whitelisted systems to deploy to. To achieve this, use the following syntax for the placement constraint:
```
[["hostname", "LIKE", "10.0.0.159|10.0.1.202|10.0.3.3"]]
```

You must include spare capacity in this list, so that if one of the whitelisted systems goes down, there is still enough room to repair your service (via [`pod replace`](#replace-a-pod)) without requiring that system.

#### Example
In order to define placement constraints as part of an install or update of a service they should be provided as a JSON encoded string.  For example one can define a placement constraint in an options file as follows:
```bash
$ cat options.json
{
  "hello": {
    "placement": "[[\"hostname\", \"UNIQUE\"]]"
  }
}
```
This file can be referenced to install a `hello-world` service.
```bash
$ dcos package install hello-world --options=options.json
```
Likewise this file can be referenced to update a `hello-world` service.
```bash
$ dcos hello-world update start --options=options.json
```

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
