---
post_title: Connecting Clients
menu_order: 50
post_excerpt: ""
enterprise: 'no'
---

Clients communicating with Apache Cassandra use the Cassandra Query Language (CQL) to issue queries and write data to the cluster. CQL client libraries exist in many languages, and Apache Cassandra ships with a utility called `cqlsh` that enables you to issue queries against an Apache Cassandra cluster from the command line.

# Discovering Endpoints

Once the service is running, you may view information about its endpoints via either of the following methods:
- CLI:
  - List endpoint types: `dcos beta-cassandra endpoints`
  - View endpoints for an endpoint type: `dcos beta-cassandra endpoints <endpoint>`
- Web:
  - List endpoint types: `<dcos-url>/service/cassandra/v1/endpoints`
  - View endpoints for an endpoint type: `<dcos-url>/service/cassandra/v1/endpoints/<endpoint>`

By default, the DC/OS Apache Cassandra Service exposes only the `native-client` endpoint type, which shows the locations for all Cassandra nodes in the cluster. If you have enabled the Thrift protocol in your service configuration, a `thrift-client` endpoint will also be listed. To see node addresses for use by native clients, such as `cqlsh`, run `dcos beta-cassandra endpoints native-client`. A typical response will look like the following:

```json
{
  "address": [
    "10.0.0.49:9042",
    "10.0.2.253:9042",
    "10.0.1.27:9042"
  ],
  "dns": [
    "node-2-server.cassandra.autoip.dcos.thisdcos.directory:9042",
    "node-0-server.cassandra.autoip.dcos.thisdcos.directory:9042",
    "node-1-server.cassandra.autoip.dcos.thisdcos.directory:9042"
  ]
}
```

In general, the `.autoip.dcos.thisdcos.directory` endpoints will only work from within the same DC/OS cluster. From outside the cluster you can either use the direct IPs or set up a proxy service that acts as a frontend to your DC/OS Apache Cassandra instance. If you use the IPs, keep in mind that they may change if nodes are moved between machines, whereas the `.autoip.dcos.thisdcos.directory` hosts will follow the nodes. For development and testing purposes, you can use [DC/OS Tunnel](https://docs.mesosphere.com/latest/administration/access-node/tunnel/) to access services from outside the cluster, but this option is not suitable for production use.

# Connecting Clients to Endpoints

To connect to a DC/OS Apache Cassandra cluster using `cqlsh`, first SSH into a host in your DC/OS cluster:
```
dcos node ssh --leader --master-proxy
```

Then, use the `cassandra` Docker image to run `cqlsh`, passing as an argument the address of one of the Apache Cassandra nodes in the cluster:
```
docker run cassandra:3.0.13 cqlsh node-0-server.cassandra.autoip.dcos.thisdcos.directory
```

This will open an interactive shell from which you can issue queries and write to the cluster. To ensure that the `cqlsh` client and your cluster are using the same CQL version, be sure to use the version of the `cassandra` Docker image that corresponds to the version of Apache Cassandra being run in your cluster. The version installed by the DC/OS Apache Cassandra Service is 3.0.13.
