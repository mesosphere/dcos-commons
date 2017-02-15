DC/OS Elasticsearch Service Guide
=================================

# Overview

DC/OS Elasticsearch is an automated service that makes it easy to deploy and manage Elasticsearch 5 and Kibana 5 with X-Pack on Mesosphere DC/OS, eliminating nearly all of the complexity traditionally associated with managing an Elasticsearch cluster. Elasticsearch is a distributed, multitenant-capable, full-text search engine with an HTTP web interface and schema-free JSON documents. Elasticsearch clusters are highly available, fault tolerant, and durable. For more information on Elasticsearch, Kibana, and X-Pack, visit the [Elastic](https://www.elastic.co/) site. Multiple Elasticsearch clusters can be installed on DC/OS and managed independently, so you can offer Elasticsearch as a managed service to your organization.

## Benefits

DC/OS Elasticsearch offers the following benefits of a semi-managed service:

*   Easy installation
*   Elastic scaling of nodes
*   Replication for high availability
*   Elasticsearch cluster and node monitoring

## Features

DC/OS Elasticsearch provides the following features:

*   Single-command installation for rapid provisioning
*   Multiple clusters for multiple tenancy with DC/OS
*   High availability runtime configuration and software updates
*   Storage volumes for enhanced data durability, known as Mesos Dynamic Reservations and Persistent Volumes
*   Automatic reporting of Elasticsearch metrics to DC/OS statsd collector

# Quick Start

1. Install an Elasticsearch cluster with Kibana and log on to the Mesos master node.

  ```bash
  dcos package install --app elastic
  dcos node ssh --master-proxy --leader
  ```

1. Wait until the cluster is deployed and the nodes are all running. This may take 5-10 minutes. If you try to access the cluster too soon, you may get an empty response or an authentication error like this:

  ```json
  {"error":{"root_cause":[{"type":"security_exception","reason":"failed to authenticate user [elastic]","header":{"WWW-Authenticate":"Basic realm=\"security\" charset=\"UTF-8\""}}],"type":"security_exception","reason":"failed to authenticate user [elastic]","header":{"WWW-Authenticate":"Basic realm=\"security\""}}}
  ```
  
1. You can check the status of the deployment via the CLI:
  
  ```bash
  dcos elastic plan show deploy
  ```

1. Explore your cluster.

  ```bash
  curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/health?v'
  curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/nodes?v'
  ```

1. Create and check indices.

  ```bash
  curl -s -u elastic:changeme -XPUT 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer?pretty'
  curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/indices?v'
  ```

1. Store and retrieve coordinator.

  ```bash
  curl -s -u elastic:changeme -XPUT 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty' -d '
  {
    "name": "John Doe"
  }'
  curl -s -u elastic:changeme -XGET 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty'
  ```

5. Check status.

  ```bash
  curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/health?v'
  curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/indices?v'
  curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/nodes?v'
  ```


### Note: if you did not install coordinator nodes, you should direct all queries to your data nodes instead:

```bash
curl -s -u elastic:changeme 'data.elastic.l4lb.thisdcos.directory:9200/_cat/nodes?v'
```
 
# Access Kibana

1. Log into your DC/OS cluster so that you can see the Dashboard. You should see your Elastic service running under Services.

1. Make sure Kibana is ready for use. The `kibana-deploy` phase should be marked as `COMPLETE` when you check the plan status:

  ```bash
  dcos elastic plan show deploy
  ```

  Depending on your Kibana node’s resources, it can take ~10 minutes to launch. If you look in the stdout log for the Kibana task, you will see this line takes the longest: 

  ```
  Optimizing and caching browser bundles...
  ```
  
  Then you’ll see this:
  
  ```
  {"type":"log","@timestamp":"2016-12-08T22:37:46Z","tags":["listening","info"],"pid":12263,"message":"Server running at http://0.0.0.0:5601"}
  ```
  
1. Then, go to this URL: 
  ```
  http://$DCOS_URL/service/{{cluster-name}}/kibana/login
  ```
  And log in with `elastic`/`changeme`

<a name="install-and-customize"></a>
# Install and Customize

## Default Installation

To start a basic cluster with three master nodes, two data nodes, one ingest node, one coordinator node, and one Kibana instance, run the following command on the DC/OS CLI:

```bash
dcos package install --app elastic
```

This command creates a new Elasticsearch cluster with the default name `elastic`. Two clusters cannot share the same name, so installing additional clusters beyond the default cluster requires customizing the `name` at install time for each additional instance.

## Custom Installation

You can customize the Elasticsearch cluster in a variety of ways by specifying a JSON options file. For example, here is a sample JSON options file that customizes the service name, master transport port, and plugins:

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

The command below creates a cluster using a `custom.json` file:

```bash
dcos package install --app --options=custom.json elastic
```

## Multiple Elasticsearch Cluster Installation

Installing multiple Elasticsearch clusters is identical to installing Elasticsearch clusters with custom configurations as described above. The only requirement on the operator is that a unique `name` is specified for each installation.

Sample JSON options file named `custom_name.json`:

    {
        "service": {
            "name": "another-cluster"
        }
    }

The command below creates a cluster using `custom_name.json`:

```bash
dcos package install --app --options=custom_name.json elastic
```

See [Configuring](#configuring) for a list of fields that can be customized via an options JSON file when the Elasticsearch cluster is created.


## Uninstall

To uninstall a cluster, replace `name` with the name of the Elasticsearch instance to be uninstalled.

```bash
dcos package uninstall --app-id=<name> elastic
```

Then, use the [framework cleaner script](https://docs.mesosphere.com/1.8/usage/managing-services/uninstall/#framework-cleaner) to remove your Elasticsearch instance from Zookeeper and destroy all data associated with it. The script requires several arguments. The default values to be used are:

- `framework_role` is `<service-name>-role`.
- `framework_principal` is `<service-name>-principal`.
- `zk_path` is `dcos-service-<service-name>`.

These values may vary if you customized them during installation. For instance, if you changed the Elasticsearch cluster name to `customers` then instead of 

- `framework_role` is `elastic-role`.
- `framework_principal` is `elastic-principal`.
- `zk_path` is `dcos-service-elastic`.

you would use

- `framework_role` is `customers-role`.
- `framework_principal` is `customers-principal`.
- `zk_path` is `dcos-service-customers`.

If you are using the Enterprise Edition of DC/OS with authentication enabled, you will need to include the token in the GET command.

```bash
AUTH_TOKEN=$(dcos config show core.dcos_acs_token)
dcos node ssh --master-proxy --leader
docker run mesosphere/janitor /janitor.py -r elastic-role -p elastic-principal -z dcos-service-elastic --auth_token=$AUTH_TOKEN
```

<a name="configuring"></a>
# Configuring

## Changing Configuration at Runtime

You can customize your cluster in-place when it is up and running. These are the general steps to follow:

1.  View your DC/OS dashboard at `http://$DCOS_URI/#/services/overview`
1.  In the list of `Applications`, click the name of the Elasticsearch service to be updated.
1.  Within the Elasticsearch instance details view, click the `Configuration` tab, then click `Edit`.
1.  In the dialog that appears, expand the `Environment Variables` section and update any field(s) to their desired value(s). For example, to increase the number of data nodes, edit the value for `DATA_NODE_COUNT`. Do not edit the value for `FRAMEWORK_NAME`, `MASTER_NODE_TRANSPORT_PORT`, or any of the disk type/size fields.
1.  Click `Change and deploy configuration` to apply any changes and cleanly reload the Elasticsearch service scheduler. The Elasticsearch cluster itself will persist across the change.

## Configuration guidelines

- Service name: This needs to be unique for each instance of the framework that is running. It is also used as your cluster name.
- Service user. This must be a non-root user that already exists on each agent. The default user for CoreOS-based clusters is core.
- Plugins: X-Pack will already be installed for you, but you can specify other plugins via a comma-separated list of plugin names (e.g., “analysis-icu”) or plugin URIs.
- CPU/RAM/Disk/Heap: These will be specific to your DC/OS cluster and your Elasticsearch use cases. Please refer to Elastic’s guidelines for configuration.
- Node counts: At least 1 data node is required for the cluster to operate at all. You do not need to use a coordinator node unless you are using Kibana. There is no maximum for node counts.
- If you are running Kibana, make sure you have exactly 1 proxylite task running. Pick an available port for it to listen on. If you are not running Kibana, do not run a proxylite task.
- Master transport port: You can pick whichever port works for your DC/OS cluster. The default is 9300. If you want multiple master nodes from different clusters on the same host, specify different master HTTP and transport ports for each cluster. If you want to ensure a particular distribution of nodes of one task type (e.g., master nodes spread across 3 racks, data nodes on one class of machines), specify this via the Marathon placement constraint. 
- Serial vs Parallel deployment. By default, the Elastic framework tells Mesos to install and update everything in parallel. You can change this to serial in order to have each node installed one at a time.

It can be confusing to understand which parts of the Elasticsearch cluster can be modified through the Mesosphere DC/OS framework at runtime, what gets specified initially and is immutable, and what gets modified directly through the Elasticsearch cluster update settings API. The most important settings are the immutable ones, so let’s start with those.

### Immutable settings (at cluster creation time via Elasticsearch package UI or JSON options file via CLI)

- Service name (aka cluster name). Can be hyphenated, but not underscored.
- Master transport port.
- Disk sizes/types.

### Modifiable settings (at runtime via Marathon env vars):

- Plugins
- CPU
- RAM
- JVM Heap (do not exceed ½ available node RAM)
- Node counts (up, not down)
- Deployment/Upgrade strategy (serial/parallel). Note that serial deployment does not yet wait for the cluster to reach green before proceeding to the next node. This is a known limitation.

Any other modifiable settings are covered by the various Elasticsearch APIs (cluster settings, index settings, templates, aliases, scripts). It’s possible that some of the more common cluster settings will get exposed in future versions of the Elasticsearch DC/OS framework.

# Viewing Plans via the CLI

You can view the deploy plan for the Elasticsearch framework via the service URL: `http://$DCOS_URL/service/dcos-{{cluster-name}}/v1/plans`

# Topology

Each task in the cluster performs one and only one of the following roles: master, data, ingest, coordinator, kibana. 

The default placement strategy distributes all instances of the same node task type to different agents. So no two master nodes would run on the same agent, but a data node might get placed onto an agent that is also running a master node.

![agent](https://s3.amazonaws.com/loren-elastic-assets/Default+Specialized+by+Agent+-+Page+1.png "Private Nodes Grouped by Agent")
![vip](https://s3.amazonaws.com/loren-elastic-assets/Default+Specialized+-+Page+1.png "Private Nodes Grouped by Named VIP")

No matter how big or small the cluster is, there will always be exactly 3 master-only nodes with `minimum_master_nodes = 2`.

## Default Topology (with minimum resources to run on 3 agents)

- 3 master-only nodes
- 2 data-only nodes
- 1 ingest-only node
- 1 Kibana node with X-Pack installed
- 1 coordinator node

The master/data/ingest/coordinator nodes are set up to only perform their one role. That is, master nodes do not store data, and ingest nodes do not store cluster state. This is how Elastic (the company) wants the clusters to look in order to support them commercially. It may seem like over-specialization for small clusters. But for medium and large clusters, it is a safer strategy. The predominant theme for this topology is “safety”. Down the road, we intend to offer a "staging" topology with a theme of “economy,” so you could install a simple 1, 2, or 3 node cluster with each node performing all roles. 

## Minimal Topology

You can set up a minimal development/staging cluster without ingest nodes, coordinator nodes, or Kibana. You’ll still get 3 master nodes placed on 3 separate hosts. If you don’t care about replication, you can even use just 1 data node. By default, Elasticsearch creates indices with a replication factor of 1 (i.e., 1 primary shard + 1 replica), so with 1 data node, your cluster will be stuck in a ‘yellow’ state unless you change the replication factor.

Note that with X-Pack installed, the default monitoring behavior is to try to write to an ingest node every few seconds. Without an ingest node, you will see frequent warnings in your master node error logs. While they can be ignored, you can turn them off by disabling X-Pack monitoring in your cluster, like this:

```bash
curl -XPUT -u elastic:changeme master.elastic.l4lb.thisdcos.directory:9200/_cluster/settings -d '{
    "persistent" : {
        "xpack.monitoring.collection.interval" : -1
    }
}'
```

# Statsd reporting

For EE clusters, the Elastic framework automatically installs the [statsd plugin](https://github.com/Automattic/elasticsearch-statsd-plugin) on all elasticsearch nodes to report metrics to the DC/OS Metrics Collector. You access elasticsearch’s metrics as well as the default DC/OS metrics by querying each agent node individually:

1. Use the dcos CLI to get the auth token: `dcos config show core.dcos_acs_token`
1. Ssh into an agent node
1. Index a few documents, send some queries
1. Within a minute, you should be able to query the endpoint and get results:

`curl -s -H "Authorization: token=your_auth_token" http://localhost:61001/system/v1/metrics/v0/containers  | jq`

Pick a container ID

`curl -s -H "Authorization: token=your_auth_token" http://localhost:61001/system/v1/metrics/v0/containers/{container-id}/app  | jq`

The response will contain elasticsearch-specific metrics like this:
```
    {
      "name": "elasticsearch.elastic.node.data-0-server.thread_pool.warmer.largest",
      "value": 2,
      "unit": "",
      "timestamp": "2017-01-31T23:24:44Z"
    },
    {
      "name": "elasticsearch.elastic.node.data-0-server.thread_pool.warmer.completed",
      "value": 2221,
      "unit": "",
      "timestamp": "2017-01-31T23:24:44Z"
    },
```

Metric names are formed based on the [formats described here](https://github.com/Automattic/elasticsearch-statsd-plugin#stats-key-formats). Scroll up to [configuration](https://github.com/Automattic/elasticsearch-statsd-plugin#configuration) to see how PREFIX and NODE_NAME get determined. In the case of a master node failover, the counts start from 0 again.


<a name="limitations"></a>
# Limitations

## Managing Configurations Outside of the Service

The Elasticsearch service's core responsibility is to deploy and maintain the deployment of an Elasticsearch cluster whose configuration has been specified. In order to do this, the service makes the assumption that it has ownership of node configuration. If an end-user makes modifications to individual nodes through out-of-band configuration operations, the service will almost certainly override those modifications at a later time. If a node crashes, it will be restarted with the configuration known to the scheduler, not with one modified out-of-band. If a configuration update is initiated, all out-of-band modifications will be overwritten during the rolling update.

## Nodes

The maximum number of deployable nodes is constrained by the DC/OS cluster's resources. Each Elasticsearch node has specified required resources, so nodes may not be placed if the DC/OS cluster lacks the requisite resources.
