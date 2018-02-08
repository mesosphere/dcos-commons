---
layout: layout.pug
navigationTitle:
excerpt:
title: Install and Customize
menuWeight: 20

techName: Apache Cassandra
packageName: beta-cassandra
serviceName: cassandra
---

{% include services/install.md
    techName=page.techName
    packageName=page.packageName
    serviceName=page.serviceName
    minNodeCount="three"
    defaultInstallDescription="with three Apache Cassandra nodes"
    serviceAccountInstructionsUrl="https://docs.mesosphere.com/services/cassandra/cass-auth/" %}

# Multi-datacenter deployment

To replicate data across data centers, {{ page.techName }} requires that you configure each cluster with the addresses of the seed nodes from every remote cluster. Here's what starting a multi-data-center {{ page.techName }} deployment would look like, running inside of a single DC/OS cluster.

## Launch two Cassandra clusters

Launch the first cluster with the default configuration:

```shell
dcos package install {{ page.packageName }}
```

Create an `options.json` file for the second cluster that specifies a different service name and data center name:

```json
{
  "service": {
    "name": "{{ page.serviceName }}2",
    "data_center": "dc2"
  }
}
```

Launch the second cluster with these custom options:
```
dcos package install {{ page.packageName }} --options=<options.json>
```

## Get the seed node IP addresses

**Note:** If your Cassandra clusters are not on the same network, you must set up a proxying layer to route traffic.

Get the list of seed node addresses for the first cluster:

```shell
dcos {{ page.packageName }} --name={{ page.serviceName }} endpoints node
```

Alternatively, you can get this information from the scheduler HTTP API:

```json
DCOS_AUTH_TOKEN=$(dcos config show core.dcos_acs_token)
DCOS_URL=$(dcos config show core.dcos_url)
curl -H "authorization:token=$DCOS_AUTH_TOKEN" $DCOS_URL/service/{{ page.serviceName }}/v1/endpoints/node
```

Your output will resemble:

```
{
  "address": [
    "10.0.1.236:9042",
    "10.0.0.119:9042"
  ],
  "dns": [
    "node-0-server.{{ page.serviceName }}.autoip.dcos.thisdcos.directory:9042",
    "node-1-server.{{ page.serviceName }}.autoip.dcos.thisdcos.directory:9042"
  ],
  "vip": "node.{{ page.serviceName }}.l4lb.thisdcos.directory:9042"
}
```

Note the IPs in the `address` field.

Run the same command for your second Cassandra cluster and note the IPs in the `address` field:

```
dcos {{ page.packageName }} --name={{ page.serviceName }}2 endpoints node
```

## Update configuration for both clusters

Create an `options2.json` file with the IP addresses of the first cluster (`{{ page.serviceName }}`):

```json
{
  "service": {
    "remote_seeds": "10.0.1.236:9042,10.0.0.119:9042"
  }
}
```

Update the configuration of the second cluster:

```
dcos {{ page.packageName }} --name={{ page.serviceName}}2 update start --options=options2.json
```

Perform the same operation on the first cluster, creating an `options.json` which contains the IP addresses of the second cluster (`{{ page.serviceName }}2`)'s seed nodes in the `service.remote_seeds` field. Then, update the first cluster's configuration: `dcos {{ page.packageName }} --name={{ page.serviceName }} update start --options=options.json`.

Both schedulers will restart after each receives the configuration update, and each cluster will communicate with the seed nodes from the other cluster to establish a multi-data-center topology. Repeat this process for each new cluster you add.

You can monitor the progress of the update for the first cluster:

```shell
dcos {{ page.packageName }} --name={{ page.serviceName }} update status
```

Or for the second cluster:

```shell
dcos {{ page.packageName }} --name={{ page.serviceName }}2 update status
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

Follow the [quick start guide](../quick-start/) to write data to one cluster. Then, use the client on the other cluster to ensure the data has propagated.
