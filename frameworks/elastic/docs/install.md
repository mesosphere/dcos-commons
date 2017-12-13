---
post_title: Install and Customize
menu_order: 20
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

## TLS

The Elastic service can be launched with TLS encryption. Enabling TLS will switch all internal communication between Elastic nodes to encrypted connections.

Enabling TLS is only possible in `permissive` and `strict` cluster security modes on Enterprise DC/OS. Both modes require a service account. Additionally, a service account must have the `dcos:superuser` permission. If the permission is missing the Elastic scheduler will not abe able to provision TLS artifacts.

Installing Elastic with TLS support requires enabling the [X-Pack](x-pack.md).

Sample JSON options file named `elastic-tls.json`:
```json
{
  "service": {
    "service_account_secret": "elastic",
    "service_account": "elastic",
    "tls": true
  },
  "elasticsearch": {
    "xpack_enabled": true
  }
}
```

For more information about TLS in the SDK see [the TLS documentation](https://mesosphere.github.io/dcos-commons/developer-guide.html#tls).

### Clients

Clients connecting to the Elastic service are required to use [the DC/OS CA bundle](https://docs.mesosphere.com/1.10/networking/tls-ssl/get-cert/) to verify the TLS connections.

### Kibana

When the Elastic service is deployed on DC/OS with TLS support Kibana, acting as an Elastic client, must be configured to verify TLS with the DC/OS CA bundle. To install the DC/OS CA bundle launch Kibana with the following configuration.

Sample JSON options file named `kibana-tls.json`:
```json
{
    "kibana": {
        "xpack_enabled": true,
        "elasticsearch_url": "https://coordinator.elastic.l4lb.thisdcos.directory:9200",
        "elasticsearch_tls": true,
        "...": "..."
    }
}
```

Similarly to Elastic, Kibana requires [X-Pack](x-pack.md) to be installed. The Kibana package itself doesn't support exposing itself over a TLS connection.

# Changing Configuration at Runtime

<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

The instructions below describe how to update the configuration for a running DC/OS service.

## Enterprise DC/OS 1.10

Enterprise DC/OS 1.10 introduces a convenient command line option that allows for easier updates to a service's configuration, as well as allowing users to inspect the status of an update, to pause and resume updates, and to restart or complete steps if necessary.

### Prerequisites

+ Enterprise DC/OS 1.10 or newer.
+ Service with a version greater than 2.0.0-x.
+ [The DC/OS CLI](https://docs.mesosphere.com/latest/cli/install/) installed and available.
+ The service's subcommand available and installed on your local machine.
  + You can install just the subcommand CLI by running `dcos package install --cli beta-elastic`.
  + If you are running an older version of the subcommand CLI that doesn't have the `update` command, uninstall and reinstall your CLI.
    ```bash
    dcos package uninstall --cli beta-elastic
    dcos package install --cli beta-elastic
    ```

### Preparing configuration

If you installed this service with Enterprise DC/OS 1.10, you can fetch the full configuration of a service (including any default values that were applied during installation). For example:

```bash
$ dcos beta-elastic describe > options.json
```

Make any configuration changes to this `options.json` file.

If you installed this service with a prior version of DC/OS, this configuration will not have been persisted by the the DC/OS package manager. You can instead use the `options.json` file that was used when [installing the service](#initial-service-configuration).

**Note:** You must specify all configuration values in the `options.json` file when performing a configuration update. Any unspecified values will be reverted to the default values specified by the DC/OS service. See the "Recreating `options.json`" section below for information on recovering these values.

#### Recreating `options.json` (optional)

If the `options.json` from when the service was last installed or updated is not available, you will need to manually recreate it using the following steps.

First, we'll fetch the default application's environment, current application's environment, and the actual template that maps config values to the environment:

1. Ensure you have [jq](https://stedolan.github.io/jq/) installed.
1. Set the service name that you're using, for example:
```bash
$ SERVICE_NAME=beta-elastic
```
1. Get the version of the package that is currently installed:
```bash
$ PACKAGE_VERSION=$(dcos package list | grep $SERVICE_NAME | awk '{print $2}')
```
1. Then fetch and save the environment variables that have been set for the service:
```bash
$ dcos marathon app show $SERVICE_NAME | jq .env > current_env.json
```
1. To identify those values that are custom, we'll get the default environment variables for this version of the service:
```bash
$ dcos package describe --package-version=$PACKAGE_VERSION --render --app $SERVICE_NAME | jq .env > default_env.json
```
1. We'll also get the entire application template:
```bash
$ dcos package describe $SERVICE_NAME --app > marathon.json.mustache
```

Now that you have these files, we'll attempt to recreate the `options.json`.

1. Use JQ and `diff` to compare the two:
```bash
$ diff <(jq -S . default_env.json) <(jq -S . current_env.json)
```
1. Now compare these values to the values contained in the `env` section in application template:
```bash
$ less marathon.json.mustache
```
1. Use the variable names (e.g. `{{service.name}}`) to create a new `options.json` file as described in [Initial service configuration](#initial-service-configuration).

### Starting the update

Once you are ready to begin, initiate an update using the DC/OS CLI, passing in the updated `options.json` file:

```bash
$ dcos beta-elastic update start --options=options.json
```

You will receive an acknowledgement message and the DC/OS package manager will restart the Scheduler in Marathon.

See [Advanced update actions](#advanced-update-actions) for commands you can use to inspect and manipulate an update after it has started.

### Open Source DC/OS, Enterprise DC/OS 1.9 and earlier

If you do not have Enterprise DC/OS 1.10 or later, the CLI commands above are not available. For Open Source DC/OS of any version, or Enterprise DC/OS 1.9 and earlier, you can perform changes from the DC/OS GUI.

<!-- END DUPLICATE BLOCK -->

These are the general steps to follow:

1.  View your DC/OS dashboard at `http://$DCOS_URI/#/services/overview`
1.  In the list of `Applications`, click the name of the Elastic service to be updated.
1.  Within the Elastic instance details view, click the `Configuration` tab, then click `Edit`.
1.  In the dialog that appears, expand the `Environment Variables` section and update any field(s) to their desired value(s). For example, to increase the number of data nodes, edit the value for `DATA_NODE_COUNT`. Do not edit the value for `FRAMEWORK_NAME`, `MASTER_NODE_TRANSPORT_PORT`, or any of the disk type/size fields.
1.  Click `Change and deploy configuration` to apply any changes and cleanly reload the Elastic service scheduler. The Elastic cluster itself will persist across the change.

# Configuration Guidelines

- Service name: This needs to be unique for each instance of the service that is running. It is also used as your cluster name.
- Service user: This must be a non-root user that already exists on each agent. The default user is `nobody`.
- X-Pack is not installed by default, but you can enable it. X-Pack comes with a 30-day trial license.
- Health check credentials: If you have X-Pack enabled, the health check will use these credentials for authorization. We recommend you create a specific Elastic user/password for this with minimal capabilities rather than using the default superuser `elastic`.  
- Plugins: You can specify other plugins via a comma-separated list of plugin names (e.g., “analysis-icu”) or plugin URIs.
- CPU/RAM/Disk/Heap: These will be specific to your DC/OS cluster and your Elasticsearch use cases. Please refer to Elastic’s guidelines for configuration.
- Node counts: At least 1 data node is required for the cluster to operate at all. You do not need to use a coordinator node. Learn about Elasticsearch node types [here](https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-node.html). There is no maximum for node counts.
- Master transport port: You can pick whichever port works for your DC/OS cluster. The default is 9300. If you want multiple master nodes from different clusters on the same host, specify different master HTTP and transport ports for each cluster. If you want to ensure a particular distribution of nodes of one task type (e.g., master nodes spread across 3 racks, data nodes on one class of machines), specify this via the Marathon placement constraint.
- Serial vs Parallel deployment. By default, the DC/OS Elastic Service tells DC/OS to install everything in parallel. You can change this to serial in order to have each node installed one at a time.
- Serial vs Parallel update. By default, the DC/OS Elastic Service tells DC/OS to update everything serially. You can change this to parallel in order to have each node updated at the same time. This is required, for instance, when you turn X-Pack on or off.
- Custom YAML can be appended to `elasticsearch.yml` on each node


## Immutable settings (at cluster creation time via Elastic package UI or JSON options file via CLI)

These setting cannot be changed after installation.

- Service name (aka cluster name). Can be hyphenated, but not underscored.
- Master transport port.
- Disk sizes/types.

## Modifiable settings (at runtime via Marathon env vars):

- Plugins
- CPU
- Memory
- JVM Heap (do not exceed ½ available node RAM)
- Node count (up, not down)
- Health check credentials
- X-Pack enabled/disabled
- Deployment/Upgrade strategy (serial/parallel). Note that serial deployment does not yet wait for the cluster to reach green before proceeding to the next node. This is a known limitation.
- Custom `elasticsearch.yml`

Any other modifiable settings are covered by the various Elasticsearch APIs (cluster settings, index settings, templates, aliases, scripts). It’s possible that some of the more common cluster settings will get exposed in future versions of the Elastic DC/OS Service.

# Viewing Plans via the CLI

You can view the deploy plan for the DC/OS Elastic Service via the service URL: `http://$DCOS_URL/service/$SERVICE_NAME/v1/plans`

# Topology

Each task in the cluster performs one and only one of the following roles: master, data, ingest, coordinator.

The default placement strategy specifies no constraint except that all the master nodes are distributed to different agents. You can specify further [Marathon placement constraints](http://mesosphere.github.io/marathon/docs/constraints.html) for each node type. For example, you can specify that data nodes are never colocated, or that ingest nodes are deployed on a rack with high-CPU servers.

![agent](img/private-nodes-by-agent.png)
![vip](img/private-node-by-vip.png)

No matter how big or small the cluster is, there will always be exactly 3 master-only nodes with `minimum_master_nodes = 2`.

## Default Topology (with minimum resources to run on 3 agents)

- 3 master-only nodes
- 2 data-only nodes
- 1 coordinator-only node
- 0 ingest-only node

The master/data/ingest/coordinator nodes are set up to only perform their one role. That is, master nodes do not store data, and ingest nodes do not store cluster state.

## Minimal Topology

You can set up a minimal development/staging cluster without ingest nodes, or coordinator nodes. You’ll still get 3 master nodes placed on 3 separate hosts. If you don’t care about replication, you can even use just 1 data node. By default, Elasticsearch creates indices with a replication factor of 1 (i.e., 1 primary shard + 1 replica), so with 1 data node, your cluster will be stuck in a ‘yellow’ state unless you change the replication factor.

Note that with X-Pack installed, the default monitoring behavior is to try to write to an ingest node every few seconds. Without an ingest node, you will see frequent warnings in your master node error logs. While they can be ignored, you can turn them off by disabling X-Pack monitoring in your cluster, like this:

```bash
$ curl -XPUT -u elastic:changeme master.elastic.l4lb.thisdcos.directory:9200/_cluster/settings -d '{
    "persistent" : {
        "xpack.monitoring.collection.interval" : -1
    }
}'
```
