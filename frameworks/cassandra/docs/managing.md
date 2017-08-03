---
post_title: Managing
nav_title: Managing
menu_order: 80
post_excerpt: ""
feature_maturity: preview
enterprise: 'no'
---

# Updating Configuration

Edit the runtime environment of the scheduler to make configuration changes. After making a change, the scheduler will be restarted, and it will automatically deploy any detected changes to the service, one node at a time. For example, a given change will first be applied to the `node-0` pod, then `node-1`, and so on.

Nodes are configured with a "Readiness check" to ensure that the underlying service appears to be in a healthy state before continuing with applying a given change to the next node in the sequence. However, this basic check is not foolproof and reasonable care should be taken to ensure that a given configuration change will not negatively affect the behavior of the service.

Some changes, such as decreasing the number of nodes or changing volume requirements, are not supported after initial deployment. See [Limitations](#limitations).

To see a full listing of available options, run `dcos package describe --config beta-cassandra` in the CLI, or browse the DC/OS Apache Cassandra Service install dialog in the DC/OS Dashboard.

## Adding a Node

The service deploys 3 nodes by default. This may be customized at initial deployment or after the cluster is already running via the `NODES` environment variable. Shrinking the cluster is not supported. If you decrease this value, the scheduler will complain about the configuration change until it's reverted back to its original value or a larger one.

## Resizing a Node

The CPU and Memory requirements of each node may be increased or decreased as follows:
- CPU (1.0 = 1 core): `CASSANDRA_CPUS`
- Memory (in MB): `CASSANDRA_MEMORY_MB`. To prevent out of memory errors, you must ensure that the `TASKCFG_ALL_CASSANDRA_HEAP_SIZE` environment variable is less than `$CASSANDRA_MEMORY_MB`.

Note: volume requirements (type and/or size) can not be changed after initial deployment.

## Updating Placement Constraints

Placement constraints may be updated after initial deployment using the following procedure. See [Service Settings](#service-settings) above for more information on placement constraints.

Let's say we have the following deployment of our nodes

- Placement constraint of: `hostname:LIKE:10.0.10.3|10.0.10.8|10.0.10.26|10.0.10.28|10.0.10.84`
- Tasks:
```
10.0.10.3: node-0
10.0.10.8: node-1
10.0.10.26: node-2
10.0.10.28: empty
10.0.10.84: empty
```

`10.0.10.8` is being decommissioned and we should move away from it. Steps:

1. Remove the decommissioned IP and add a new IP to the placement rule whitelist by editing `PLACEMENT_CONSTRAINT`:

	```
	hostname:LIKE:10.0.10.3|10.0.10.26|10.0.10.28|10.0.10.84|10.0.10.123
	```
1. Redeploy `node-1` from the decommissioned node to somewhere within the new whitelist: `dcos beta-cassandra pod replace node-1`
1. Wait for `node-1` to be up and healthy before continuing with any other replacement operations.

# Restarting a Node

This operation will restart a node, while keeping it at its current location and with its current persistent volume data. This may be thought of as similar to restarting a system process, but it also deletes any data that is not on a persistent volume.

1. Run `dcos beta-cassandra pod restart node-<NUM>`, e.g. `node-2`.

# Replacing a Node

This operation will move a node to a new system and will discard the persistent volumes at the prior system to be rebuilt at the new system. Perform this operation if a given system is about to be offlined or has already been offlined.

**Note:** Nodes are not moved automatically. You must perform the following steps manually to move nodes to new systems. You can automate node replacement according to your own preferences.

1. Run `dcos beta-cassandra pod replace node-<NUM>` to halt the current instance with id `<NUM>` (if still running) and launch a new instance elsewhere.

For example, let's say `node-2`'s host system has died and `node-2` needs to be moved.
```
dcos beta-cassandra pod replace node-2
```

## Seed nodes
Cassandra seed nodes are those nodes with indices smaller than the seed node count.  By default, Cassandra is deployed 
with a seed node count of two.  So, node-0 and node-1 are seed nodes. When a replace operation is performed on one these
nodes, all other nodes must be restarted to be brought up to date regarding the ip address of the new seed node. This 
operation is performed automatically.
  
For example if `node-0` needed to be replaced we would execute:

```bash
dcos beta-cassandra pod replace node-0
```

which would result in a recovery plan like the following:

```bash
$ dcos beta-cassandra --name=cassandra plan show recovery
recovery (IN_PROGRESS)
└─ permanent-node-failure-recovery (IN_PROGRESS)
   ├─ node-0:[server] (COMPLETE)
   ├─ node-1:[server] (STARTING)
   └─ node-2:[server] (PENDING)

```

**Note:** Only the seed node is being placed on a new node, all other nodes are restarted in place with no loss of data.

# Configuring Multi-data-center Deployments

To replicate data across data centers, Apache Cassandra requires that you configure each cluster with the addresses of the seed nodes from every remote cluster. Here's what starting a multi-data-center Apache Cassandra deployment would like, running inside of a single DC/OS cluster.

Launch the first cluster with the default configuration:
```
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

Get the list of seed node addresses for the first cluster from the scheduler HTTP API:
```json
DCOS_AUTH_TOKEN=$(dcos config show core.dcos_acs_token)
DCOS_URL=$(dcos config show core.dcos_url)
curl -H "authorization:token=$DCOS_AUTH_TOKEN" $DCOS_URL/service/cassandra/v1/seeds
{"seeds": ["10.0.0.1", "10.0.0.2"]}
```

In the DC/OS UI, go to the configuration dialog for the second cluster (whose service name is `cassandra2`) and update the `TASKCFG_ALL_REMOTE_SEEDS` environment variable to `10.0.0.1,10.0.0.2`. This environment variable may not already be present in a fresh install. To add it, click the plus sign at the bottom of the list of environment variables, and then fill in its name and value in the new row that appears.

Get the seed node addresses for the second cluster the same way:
```
curl -H "authorization:token=$DCOS_AUTH_TOKEN" $DCOS_URL/service/cassandra2/v1/seeds
{"seeds": ["10.0.0.3", "10.0.0.4"]}
```

In the DC/OS UI, go to the configuration dialog for the first cluster (whose service name is `cassandra`) and update the `TASKCFG_ALL_REMOTE_SEEDS` environment variable to `10.0.0.3,10.0.0.4`, again adding the variable with the plus sign if it's not already present.

Both schedulers will restart after the configuration update, and each cluster will communicate with the seed nodes from the other cluster to establish a multi-data-center topology. Repeat this process for each new cluster you add, appending a comma-separated list of that cluster's seeds to the `TASKCFG_ALL_REMOTE_SEEDS` environment variable for each existing cluster, and adding a comma-separated list of each existing cluster's seeds to the newly-added cluster's `TASKCFG_ALL_REMOTE_SEEDS` environment variable.
