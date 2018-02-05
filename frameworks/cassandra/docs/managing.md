---
layout: layout.pug
navigationTitle:
excerpt:
title: Managing
menuWeight: 60
---

{% include services/managing.md
    pod_type="node"
    task_type="server"
    tech_name="Apache Cassandra"
    package_name="beta-cassandra"
    service_name="cassandra"
    cli_package_name="beta-cassandra --name=cassandra" %}

## Performing Cassandra Cleanup and Repair Operations

You may manually trigger certain `nodetool` operations against your Cassandra instance using the CLI or the HTTP API.

### Cleanup

You may trigger a `nodetool cleanup` operation across your Cassandra nodes using the `cleanup` plan. This plan requires the following parameters to run:
- `CASSANDRA_KEYSPACE`: the Cassandra keyspace to be cleaned up.

To initiate this plan from the command line:
```
dcos cassandra plan start cleanup -p CASSANDRA_KEYSPACE=space1
```

To view the status of this plan from the command line:
```
dcos cassandra plan status cleanup
cleanup (IN_PROGRESS)
└─ cleanup-deploy (IN_PROGRESS)
   ├─ node-0:[cleanup] (COMPLETE)
   ├─ node-1:[cleanup] (STARTING)
   └─ node-2:[cleanup] (PENDING)
```

When the plan is completed, its status will be `COMPLETE`.

The above `plan start` and `plan status` commands may also be made directly to the service over HTTP. To see the queries involved, run the above commands with an additional `-v` flag.

For more information about `nodetool cleanup`, see the Cassandra documentation.

### Repair

You may trigger a `nodetool repair` operation across your Cassandra nodes using the `repair` plan. This plan requires the following parameters to run:
- `CASSANDRA_KEYSPACE`: the Cassandra keyspace to be cleaned up.

To initiate this command from the command line:
```
dcos cassandra plan start repair -p CASSANDRA_KEYSPACE=space1
```

To view the status of this plan from the command line:
```
dcos cassandra plan status repair
repair (STARTING)
└─ repair-deploy (STARTING)
   ├─ node-0:[repair] (STARTING)
   ├─ node-1:[repair] (PENDING)
   └─ node-2:[repair] (PENDING)
```

When the plan is completed, its status will be `COMPLETE`.

The above `plan start` and `plan status` commands may also be made directly to the service over HTTP. To see the queries involved, run the above commands with an additional `-v` flag.

For more information about `nodetool repair`, see the Cassandra documentation.

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
