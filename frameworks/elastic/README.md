DC/OS Elastic Service Guide
=================================

# Overview

DC/OS Elastic is an automated service that makes it easy to deploy and manage Elasticsearch 5 and Kibana 5 with X-Pack on Mesosphere DC/OS, eliminating nearly all of the complexity traditionally associated with managing an Elasticsearch cluster. Elasticsearch is a distributed, multitenant-capable full-text search engine with an HTTP web interface and schema-free JSON documents. Elasticsearch clusters are highly available, fault tolerant, and very durable. For more information on Elasticsearch, Kibana, and X-Pack, visit the [Elastic](https://www.elastic.co/) site. Multiple Elasticsearch clusters can be installed on DC/OS and managed independently, so you can offer Elasticsearch as a managed service to your organization.

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
*   Integration with syslog-compatible logging services for diagnostics and troubleshooting
*   Integration with statsd-compatible metrics services for capacity and performance monitoring
*   Node placement strategy ensures Elasticsearch nodes are not collocated with other nodes of the same type

<a name="getting-started"></a>
# Getting Started

## Quick Start

*   Step 1. Install an Elasticsearch cluster and log on.

```bash
dcos package install --app elastic
dcos node ssh --master-proxy --leader
```

*   Step 2. Explore your cluster.

```bash
curl -s -u elastic:changeme 'data.elastic.l4lb.thisdcos.directory:9200/_cat/health?v'
curl -s -u elastic:changeme 'data.elastic.l4lb.thisdcos.directory:9200/_cat/nodes?v'
```

*   Step 3. Create and check indices.

```bash
curl -s -u elastic:changeme -XPUT 'data.elastic.l4lb.thisdcos.directory:9200/customer?pretty'
curl -s -u elastic:changeme 'data.elastic.l4lb.thisdcos.directory:9200/_cat/indices?v'
```

*   Step 4. Store and retrieve data.

```bash
curl -s -u elastic:changeme -XPUT 'data.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty' -d '
{
  "name": "John Doe"
}'
curl -s -u elastic:changeme -XGET 'data.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty'
```

*   Step 5. Check status.

```bash
curl -s -u elastic:changeme 'data.elastic.l4lb.thisdcos.directory:9200/_cat/health?v'
curl -s -u elastic:changeme 'data.elastic.l4lb.thisdcos.directory:9200/_cat/indices?v'
curl -s -u elastic:changeme 'data.elastic.l4lb.thisdcos.directory:9200/_cat/nodes?v'
```


### Note: if you installed coordinator nodes, you should direct all queries to them instead of your data nodes:

```bash
curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/nodes?v'
```
 
## Kibana

To access your Kibana service running in DC/OS, you'll need to set up a HAProxy on one of your public slave nodes.

*   Step 1. From your DC/OS Dashboard, click Services in the left navigation.
 
*   Step 2. Click deploy service

*   Step 3. Toggle to JSON mode and paste in this JSON:

```json
{
    "id": "haproxy",
    "mem": 128,
    "cpus": 0.5,
    "requirePorts": true,
    "instances": 1,
    "container": {
        "type": "DOCKER",
        "docker": {
            "network": "HOST",
            "image": "sargun/haproxy-demo:3"
        }
    },
    "env": {
        "CONFIGURL": "https://gist.githubusercontent.com/loren/c4eca3cd7b638a97346843bdef62a2d4/raw/e5bf09b79c4efbe847f9f7d75c489f4616ad558b/haproxy.cfg"
    },
    "acceptedResourceRoles": [
        "slave_public"
    ]
}
```

* Step 4. (Optional) If you changed your service name from the default 'elastic' to something else, you'll need to modify the `haproxy.cfg` file accordingly.

* Step 5. Click on the `haproxy` service to see which public slave node it's running on. Let's say it's `a.b.c.d`.
 
* Step 6. Determine the publicly facing IP address.
 
 ```bash
 dcos node ssh --master-proxy --leader
 ssh a.b.c.d
 curl ifconfig.co
 ```

* Step 7. Browse to port 80 on that IP address, and you should see Kibana login. The default username/password is `elastic`/`changeme`.

<a name="install-and-customize"></a>
# Install and Customize

## Default Installation

To start a basic cluster with three master nodes, two data nodes, one ingest node, one coordinator node, and one kibana instance run the following command on the DC/OS CLI:

```bash
dcos package install --app elastic
```

This command creates a new Elasticsearch cluster with the default name `elastic`. Two clusters cannot share the same name, so installing additional clusters beyond the default cluster requires customizing the `name` at install time for each additional instance.

## Custom Installation

You can customize the Elasticsearch cluster in a variety of ways by specifying a JSON options file. Sample JSON options file named `custom_heap.json` with a JVM heap size specified for each Elasticsearch node:

    {
        "nodes": {
            "heap": {
                "size": 768
            }
        }
    }


The command below creates a cluster using the `custom_heap.json` file:

```bash
dcos package install --app --options=custom_name.json elastic
```

## Multiple Elasticsearch cluster installation

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

See [Configuration Options](#configuration-options) for a list of fields that can be customized via an options JSON file when the Elasticsearch cluster is created.


## Uninstall

Uninstalling a cluster is also straightforward. Replace `name` with the name of the elasticsearch instance to be uninstalled.

```bash
dcos package uninstall --app-id=<name> elastic
```

Then, use the [framework cleaner script](https://docs.mesosphere.com/framework_cleaner/) to remove your Elasticsearch instance from Zookeeper and to destroy all data associated with it. The script requires several arguments, the default values to be used are:

- `framework_role` is `<service-name>-role`.
- `framework_principal` is `<service-name>-principal`.
- `zk_path` is `dcos-service-<service-name>`.

These values may vary if you had customized them during installation. For instance, if you changed the Elasticsearch cluster name to `customers` then instead of 

- `framework_role` is `elastic-role`.
- `framework_principal` is `elastic-principal`.
- `zk_path` is `dcos-service-elastic`.

you would use

- `framework_role` is `customers-role`.
- `framework_principal` is `customers-principal`.
- `zk_path` is `dcos-service-customers`.



If you are using the Enterprise Edition of DC/OS with Authentication enabled you will need to include the token in the GET command.

```bash
AUTH_TOKEN=$(dcos config show core.dcos_acs_token)
dcos node ssh --master-proxy --leader
docker run mesosphere/janitor /janitor.py -r elastic-role -p elastic-principal -z dcos-service-elastic --auth_token=$AUTH_TOKEN
```

<a name="configuring"></a>
# Configuring

## Changing Configuration at Runtime

You can customize your cluster in-place when it is up and running.

The Elasticsearch scheduler runs as a Marathon process and can be reconfigured by changing values within Marathon. These are the general steps to follow:

1.  View your Marathon dashboard at `http://$DCOS_URI/marathon`
2.  In the list of `Applications`, click the name of the Elasticsearch service to be updated.
3.  Within the Elasticsearch instance details view, click the `Configuration` tab, then click the `Edit` button.
4.  In the dialog that appears, expand the `Environment Variables` section and update any field(s) to their desired value(s). For example, to [increase the number of data nodes](#node-count), edit the value for `DATA_NODE_COUNT`. Do not edit the value for `FRAMEWORK_NAME`.
5.  Click `Change and deploy configuration` to apply any changes and cleanly reload the Elasticsearch service scheduler. The Elasticsearch cluster itself will persist across the change.

<a name="limitations"></a>
# Limitations

## Managing Configurations Outside of the Service

The Elasticsearch service's core responsibility is to deploy and maintain the deployment of a Elasticsearch cluster whose configuration has been specified. In order to do this the service makes the assumption that it has ownership of node configuration. If an end-user makes modifications to individual nodes or the cluster through out-of-band configuration operations, the service will almost certainly override those modifications at a later time. If a node crashes, it will be restarted with the configuration known to the scheduler, not with one modified out-of-band. If a configuration update is initiated, all out-of-band modifications will be overwritten during the rolling update.

## Nodes

The maximum number of deployable nodes is constrained by the DC/OS cluster's resources. Each Elasticsearch node has specified required resources, so nodes may not be placed if the DC/OS cluster lacks the requisite resources. Also, only one Elasticsearch node from a single cluster may be placed on a given DC/OS agent. 
