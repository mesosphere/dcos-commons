---
post_title: Install and Customize
menu_order: 0
feature_maturity: preview
enterprise: 'no'
---

# Default Installation

To start a basic cluster with three master nodes, two data nodes, and one coordinator node, run the following command on the DC/OS CLI:

```bash
$ dcos package install beta-elastic
```

This command creates a new Elasticsearch cluster with the default name `elastic`. Two clusters cannot share the same name, so installing additional clusters beyond the default cluster requires customizing the `name` at install time for each additional instance.

**Note:** You can also install Elastic from the **Universe** > **Packages** tab of the DC/OS web interface. If you install Elastic from the web interface, you must install the Elastic DC/OS CLI subcommands separately. From the DC/OS CLI, enter:

```bash
dcos package install beta-elastic --cli
```

# Custom Installation

You can customize the Elastic cluster in a variety of ways by specifying a JSON options file. For example, here is a sample JSON options file that customizes the service name, master transport port, and plugins:

```json
{
    "service": {
        "name": "another-cluster"
    },
    "master_nodes": {
          "transport_port": 19300
    },
    "elasticsearch": {
        "plugins": "analysis-icu,analysis-kuromoji"
    }
}

```

The command below creates a cluster using a `options.json` file:

```bash
$ dcos package install beta-elastic --options=options.json
```

**Recommendation:** Store your custom configuration in source control.

# Multiple Elastic Cluster Installation

Installing multiple Elastic clusters is identical to installing Elastic clusters with custom configurations as described above. The only requirement on the operator is that a unique `name` is specified for each installation.

Sample JSON options file named `another-cluster.json`:
```json
{
    "service": {
        "name": "another-cluster"
    }
}
```

The command below creates a cluster using `another-cluster.json`:

```bash
$ dcos package install beta-elastic --options=another-cluster.json
```

See the Configuring section for a list of fields that can be customized via an options JSON file when the Elastic cluster is created.

<!-- THIS BLOCK DUPLICATES THE OPERATIONS GUIDE -->

## Integration with DC/OS access controls

In Enterprise DC/OS 1.10 and above, you can integrate your SDK-based service with DC/OS ACLs to grant users and groups access to only certain services. You do this by installing your service into a folder, and then restricting access to some number of folders. Folders also allow you to namespace services. For instance, `staging/elastic` and `production/elastic`.

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
1. Install your service into a folder called `test`. Go to **Catalog**, then search for **beta-elastic**.
1. Click **CONFIGURE** and change the service name to `/testing/elastic`, then deploy.

   The slashes in your service name are interpreted as folders. You are deploying Elastic in the `/testing` folder. Any user with access to the `/testing` folder will have access to the service.

**Important:**
- Services cannot be renamed. Because the location of the service is specified in the name, you cannot move services between folders.
- DC/OS 1.9 and earlier does not accept slashes in service names. You may be able to create the service, but you will encounter unexpected problems.

### Interacting with your foldered service

- Interact with your foldered service via the DC/OS CLI with this flag: `--name=/path/to/myservice`.
  - To interact with your foldered service over the web directly, use `http://<dcos-url>/service/path/to/myservice`. E.g., `http://<dcos-url>/service/testing/elastic/v1/endpoints`.

<!-- END DUPLICATE BLOCK -->

## Virtual networks
Elastic supports deployment on virtual networks on DC/OS (including the `dcos` overlay network), allowing each container (task) to have its own IP address and not use the ports resources on the agent. This can be specified by passing the following configuration during installation:
```json
{
    "service": {
        "virtual_network_enabled": true
    }
}
```
As mentioned in the [developer guide](https://mesosphere.github.io/dcos-commons/developer-guide.html) once the service is deployed on a virtual network, it cannot be updated to use the host network.
