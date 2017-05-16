---
post_title: Configuring
menu_order: 30
feature_maturity: preview
enterprise: 'no'
---

# Changing Configuration at Runtime

You can customize your cluster in-place when it is up and running. These are the general steps to follow:

1.  View your DC/OS dashboard at `http://$DCOS_URI/#/services/overview`
1.  In the list of `Applications`, click the name of the Elastic service to be updated.
1.  Within the Elastic instance details view, click the `Configuration` tab, then click `Edit`.
1.  In the dialog that appears, expand the `Environment Variables` section and update any field(s) to their desired value(s). For example, to increase the number of data nodes, edit the value for `DATA_NODE_COUNT`. Do not edit the value for `FRAMEWORK_NAME`, `MASTER_NODE_TRANSPORT_PORT`, or any of the disk type/size fields.
1.  Click `Change and deploy configuration` to apply any changes and cleanly reload the Elastic service scheduler. The Elastic cluster itself will persist across the change.

# Configuration Guidelines

- Service name: This needs to be unique for each instance of the service that is running. It is also used as your cluster name.
- Service user: This must be a non-root user that already exists on each agent. The default user for CoreOS-based clusters is core.
- X-Pack is not installed by default, but you can enable it. X-Pack comes with a 30-day trial license.
- Health check credentials: If you have X-Pack enabled, the health check will use these credentials for authorization. We recommend you create a specific Elastic user/password for this with minimal capabilities rather than using the default superuser `elastic`.  
- Plugins: You can specify other plugins via a comma-separated list of plugin names (e.g., “analysis-icu”) or plugin URIs.
- CPU/RAM/Disk/Heap: These will be specific to your DC/OS cluster and your Elasticsearch use cases. Please refer to Elastic’s guidelines for configuration.
- Node counts: At least 1 data node is required for the cluster to operate at all. You do not need to use a coordinator node. Learn about Elasticsearch node types [here](https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-node.html). There is no maximum for node counts.
- Master transport port: You can pick whichever port works for your DC/OS cluster. The default is 9300. If you want multiple master nodes from different clusters on the same host, specify different master HTTP and transport ports for each cluster. If you want to ensure a particular distribution of nodes of one task type (e.g., master nodes spread across 3 racks, data nodes on one class of machines), specify this via the Marathon placement constraint.
- Serial vs Parallel deployment. By default, the DC/OS Elastic Service tells DC/OS to install and update everything in parallel. You can change this to serial in order to have each node installed one at a time.


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

Any other modifiable settings are covered by the various Elasticsearch APIs (cluster settings, index settings, templates, aliases, scripts). It’s possible that some of the more common cluster settings will get exposed in future versions of the Elastic DC/OS Service.

# Viewing Plans via the CLI

You can view the deploy plan for the DC/OS Elastic Service via the service URL: `http://$DCOS_URL/service/$SERVICE_NAME/v1/plans`

# Topology

Each task in the cluster performs one and only one of the following roles: master, data, ingest, coordinator.

The default placement strategy has no restrictions other than distributing all the master nodes to different agents. You can specify the Marathon placement constraints for each node type. For example, you can specify that data nodes are never colocated, or that ingest nodes are deployed on a rack with high-CPU servers. 

![agent](img/private-nodes-by-agent.png)
![vip](img/private-node-by-vip.png)

No matter how big or small the cluster is, there will always be exactly 3 master-only nodes with `minimum_master_nodes = 2`.

## Default Topology (with minimum resources to run on 3 agents)

- 3 master-only nodes
- 2 data-only nodes
- 1 ingest-only node
- 1 coordinator node

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
