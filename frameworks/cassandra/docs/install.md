---
layout: layout.pug
navigationTitle:
excerpt:
title: Install and Customize
menuWeight: 20
---
{% assign data = site.data.services.cassandra %}

{% include services/install.md data=data %}

# Multi-datacenter deployment

To replicate data across data centers, {{ data.techName }} requires that you configure each cluster with the addresses of the seed nodes from every remote cluster. Here's what starting a multi-data-center {{ data.techName }} deployment would look like, running inside of a single DC/OS cluster.

## Launch two Cassandra clusters

Launch the first cluster with the default configuration:

```shell
dcos package install {{ data.packageName }}
```

Create an `options.json` file for the second cluster that specifies a different service name and data center name:

```json
{
  "service": {
    "name": "{{ data.serviceName }}2",
    "data_center": "dc2"
  }
}
```

Launch the second cluster with these custom options:
```
dcos package install {{ data.packageName }} --options=<options.json>
```

## Get the seed node IP addresses

**Note:** If your Cassandra clusters are not on the same network, you must set up a proxying layer to route traffic.

Get the list of seed node addresses for the first cluster:

```shell
dcos {{ data.packageName }} --name={{ data.serviceName }} endpoints node
```

Alternatively, you can get this information from the scheduler HTTP API:

```json
DCOS_AUTH_TOKEN=$(dcos config show core.dcos_acs_token)
DCOS_URL=$(dcos config show core.dcos_url)
curl -H "authorization:token=$DCOS_AUTH_TOKEN" $DCOS_URL/service/{{ data.serviceName }}/v1/endpoints/node
```

Your output will resemble:

```
{
  "address": [
    "10.0.1.236:9042",
    "10.0.0.119:9042"
  ],
  "dns": [
    "node-0-server.{{ data.serviceName }}.autoip.dcos.thisdcos.directory:9042",
    "node-1-server.{{ data.serviceName }}.autoip.dcos.thisdcos.directory:9042"
  ],
  "vip": "node.{{ data.serviceName }}.l4lb.thisdcos.directory:9042"
}
```

Note the IPs in the `address` field.

Run the same command for your second Cassandra cluster and note the IPs in the `address` field:

```
dcos {{ data.packageName }} --name={{ data.serviceName }}2 endpoints node
```

## Update configuration for both clusters

Create an `options2.json` file with the IP addresses of the first cluster (`{{ data.serviceName }}`):

```json
{
  "service": {
    "remote_seeds": "10.0.1.236:9042,10.0.0.119:9042"
  }
}
```

Update the configuration of the second cluster:

```
dcos {{ data.packageName }} --name={{ data.serviceName}}2 update start --options=options2.json
```

Perform the same operation on the first cluster, creating an `options.json` which contains the IP addresses of the second cluster (`{{ data.serviceName }}2`)'s seed nodes in the `service.remote_seeds` field. Then, update the first cluster's configuration: `dcos {{ data.packageName }} --name={{ data.serviceName }} update start --options=options.json`.

Both schedulers will restart after each receives the configuration update, and each cluster will communicate with the seed nodes from the other cluster to establish a multi-data-center topology. Repeat this process for each new cluster you add.

You can monitor the progress of the update for the first cluster:

```shell
dcos {{ data.packageName }} --name={{ data.serviceName }} update status
```

Or for the second cluster:

```shell
dcos {{ data.packageName }} --name={{ data.serviceName }}2 update status
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
