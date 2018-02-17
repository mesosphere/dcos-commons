---
layout: layout.pug
navigationTitle:
excerpt:
title: Install and Customize
menuWeight: 20
---
{% assign data = site.data.services.elastic %}

{% include services/install1.md data=data %}

### Example custom installation

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
$ dcos package install {{ data.packageName }} --options=options.json
```

**Recommendation:** Store your custom configuration in source control.

{% include services/install2.md data=data %}

## TLS

The Elastic service can be launched with TLS encryption. Enabling TLS will switch all internal communication between Elastic nodes to encrypted connections.

Enabling TLS is only possible in `permissive` and `strict` cluster security modes on Enterprise DC/OS. Both modes require a service account. Additionally, a service account must have the `dcos:superuser` permission. If the permission is missing the Elastic scheduler will not abe able to provision TLS artifacts.

Installing Elastic with TLS support requires [enabling X-Pack functionality](../elastic-x-pack/).

Sample JSON options file named `elastic-tls.json`:
```json
{
  "service": {
    "service_account_secret": "elastic",
    "service_account": "elastic",
    "security": {
        "transport_encryption": {
            "enabled": true
        }
    }
  },
  "elasticsearch": {
    "xpack_enabled": true
  }
}
```

For more information about TLS in the SDK see [the TLS documentation](https://mesosphere.github.io/dcos-commons/developer-guide.html#tls).

### Clients when TLS is enabled

Clients connecting to the Elastic service are required to use [the DC/OS CA bundle](https://docs.mesosphere.com/1.10/networking/tls-ssl/get-cert/) to verify the TLS connections.

### Kibana when TLS is enabled

When the Elastic service has been deployed on DC/OS with TLS support, Kibana, acting as an Elastic client, must be configured to verify TLS with the DC/OS CA bundle. To install the DC/OS CA bundle, launch Kibana with the following configuration.

Sample JSON options file named `kibana-tls.json`:
```json
{
    "kibana": {
        "xpack_enabled": true,
        "elasticsearch_url": "https://coordinator.{{ data.serviceName }}.l4lb.thisdcos.directory:9200",
        "elasticsearch_tls": true,
        "...": "..."
    }
}
```

Similarly to Elastic, Kibana requires [X-Pack](../elastic-x-pack/) to be installed. The Kibana package itself doesn't support exposing itself over a TLS connection.

## Configuration Guidelines

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

### Immutable settings (at cluster creation time via Elastic package UI or JSON options file via CLI)

These setting cannot be changed after installation.

- Service name (aka cluster name). Can be hyphenated, but not underscored.
- Master transport port.
- Disk sizes/types.

### Modifiable settings

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

## Topology

Each task in the cluster performs one and only one of the following roles: master, data, ingest, coordinator.

The default placement strategy specifies that no two nodes of any type are distributed to the same agent. You can specify further [Marathon placement constraints](http://mesosphere.github.io/marathon/docs/constraints.html) for each node type. For example, you can specify that ingest nodes are deployed on a rack with high-CPU servers.

![agent](/dcos-commons/services/elastic/img/private-nodes-by-agent.png)
![vip](/dcos-commons/services/elastic/img/private-node-by-vip.png)

No matter how big or small the cluster is, there will always be exactly 3 master-only nodes with `minimum_master_nodes = 2`.

### Default Topology (with minimum resources to run on 3 agents)

- 3 master-only nodes
- 2 data-only nodes
- 1 coordinator-only node
- 0 ingest-only node

The master/data/ingest/coordinator nodes are set up to only perform their one role. That is, master nodes do not store data, and ingest nodes do not store cluster state.

### Minimal Topology

You can set up a minimal development/staging cluster without ingest nodes, or coordinator nodes. You’ll still get 3 master nodes placed on 3 separate hosts. If you don’t care about replication, you can even use just 1 data node.

Note that with X-Pack installed, the default monitoring behavior is to try to write to an ingest node every few seconds. Without an ingest node, you will see frequent warnings in your master node error logs. While they can be ignored, you can turn them off by disabling X-Pack monitoring in your cluster, like this:

```bash
$ curl -XPUT -u elastic:changeme master.{{ data.serviceName }}.l4lb.thisdcos.directory:9200/_cluster/settings -d '{
    "persistent" : {
        "xpack.monitoring.collection.interval" : -1
    }
}'
```
