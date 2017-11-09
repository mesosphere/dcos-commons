---
post_title: Installing and Customizing
menu_order: 20
post_excerpt: ""
enterprise: 'no'
---

The default DC/OS Apache Cassandra installation provides reasonable defaults for trying out the service, but may not be sufficient for production use. You may require different configurations depending on the context of the deployment.

## Prerequisites
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

It is recommended that this custom configuration is stored in source control.

For more information about building the `options.json` file, see the [DC/OS documentation](https://docs.mesosphere.com/latest/usage/managing-services/config-universe-service/) for service configuration access.

## Installation from the DC/OS Web Interface

You can [install DC/OS Apache Cassandra from the DC/OS web interface](https://docs.mesosphere.com/1.9/usage/managing-services/install/). If you install DC/OS Apache Cassandra from the web interface, you must install the DC/OS Apache Cassandra CLI subcommands separately. From the DC/OS CLI, enter:
```bash
dcos package install beta-cassandra --cli
```
Choose `ADVANCED INSTALLATION` to perform a custom installation.

<!-- THIS BLOCK DUPLICATES THE OPERATIONS GUIDE -->

## Integration with DC/OS access controls

In Enterprise DC/OS 1.10 and above, you can integrate your SDK-based service with DC/OS ACLs to grant users and groups access to only certain services. You do this by installing your service into a folder, and then restricting access to some number of folders. Folders also allow you to namespace services. For instance, `staging/cassandra` and `production/cassandra`.

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
1. Install your service into a folder called `test`. Go to **Catalog**, then search for **beta-cassandra**.
1. Click **CONFIGURE** and change the service name to `/testing/cassandra`, then deploy.

   The slashes in your service name are interpreted as folders. You are deploying Cassandra in the `/testing` folder. Any user with access to the `/testing` folder will have access to the service.

**Important:**
- Services cannot be renamed. Because the location of the service is specified in the name, you cannot move services between folders.
- DC/OS 1.9 and earlier does not accept slashes in service names. You may be able to create the service, but you will encounter unexpected problems.

### Interacting with your foldered service

- Interact with your foldered service via the DC/OS CLI with this flag: `--name=/path/to/myservice`.
- To interact with your foldered service over the web directly, use `http://<dcos-url>/service/path/to/myservice`. E.g., `http://<dcos-url>/service/testing/cassandra/v1/endpoints`.

<!-- END DUPLICATE BLOCK -->

# Multi-datacenter Deployment

To replicate data across data centers, Apache Cassandra requires that you configure each cluster with the addresses of the seed nodes from every remote cluster. Here's what starting a multi-data-center Apache Cassandra deployment would look like, running inside of a single DC/OS cluster.

## Launch two Cassandra clusters

Launch the first cluster with the default configuration:

```shell
dcos package install beta-cassandra
```

Create an `options.json` file for the second cluster that specifies a different service name and data center name:

```json
{
  "service": {
    "name": "cassandra2",
    "data_center": "dc2"
  }
}
```

Launch the second cluster with these custom options:
```
dcos package install beta-cassandra --options=<options>.json
```

## Get the seed node IP addresses

**Note:** If your Cassandra clusters are not on the same network, you must set up a proxying layer to route traffic.

Get the list of seed node addresses for the first cluster:

```shell
dcos cassandra endpoints node
```

Alternatively, you can get this information from the scheduler HTTP API:

```json
DCOS_AUTH_TOKEN=$(dcos config show core.dcos_acs_token)
DCOS_URL=$(dcos config show core.dcos_url)
curl -H "authorization:token=$DCOS_AUTH_TOKEN" $DCOS_URL/service/cassandra/v1/endpoints/node
```

Your output will resemble:

```
{
  "address": [
    "10.0.1.236:9042",
    "10.0.0.119:9042"
  ],
  "dns": [
    "node-0-server.cassandra.autoip.dcos.thisdcos.directory:9042",
    "node-1-server.cassandra.autoip.dcos.thisdcos.directory:9042"
  ],
  "vip": "node.cassandra.l4lb.thisdcos.directory:9042"
}
```

Note the IPs in the `address` field.

Run the same command for your second Cassandra cluster and note the IPs in the `address` field:

```
dcos cassandra endpoints node --name=cassandra2
```

## Update configuration for both clusters

Create an `options.json` file with the IP addresses of the first cluster (`cassandra`):

```json
{
  "service": {
    "remote_seeds": "10.0.1.236:9042,10.0.0.119:9042"
  }
}
```

Update the configuration of the second cluster:

```
dcos cassandra update start --options=options.json --name=cassandra2
```

Perform the same operation on the first cluster, adding the IP addresses of the second cluster's seed nodes to the `service.remote_seeds` field. Then, update the first cluster's configuration: `dcos cassandra update start --options=options.json`.

Both schedulers will restart after the configuration update, and each cluster will communicate with the seed nodes from the other cluster to establish a multi-data-center topology. Repeat this process for each new cluster you add.

You can monitor the progress of the update:

```shell
dcos cassandra --name=cassandra update status
```

Your output will resemble:

```shell
deploy (IN_PROGRESS)
└─ node-deploy (IN_PROGRESS)
   ├─ node-0:[server] (COMPLETE)
   ├─ node-1:[server] (COMPLETE)
   └─ node-2:[server] (PREPARED)
```

## Test your multi-datacenter configuration

Follow the [quick start guide](quick-start.md) to write data to one cluster. Then, use the client on the other cluster to ensure the data has propagated.
