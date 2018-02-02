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
