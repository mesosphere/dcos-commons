---
post_title: Upgrade
menu_order: 15
feature_maturity: preview
enterprise: 'no'
---

# Overview
We support upgrade/rollback between adjacent versions only. Concretely, to upgrade from version 2 to version 4, you must upgrade from 2 -> 3, then from 3 -> 4.

# Upgrade Instructions

1. In the DC/OS web interface, destroy the Elastic instance to be updated. (This will not kill Elasticsearch node tasks).
2. Verify that you no longer see the Elastic instance in the DC/OS web interface.
3. From the DC/OS CLI, install the N+1 version of Elastic (where N is your current version) with any customizations you require in a JSON options file:

```bash
$ dcos package install elastic --options=/path/to/options.json
```

The command above will trigger the install of the new Elastic version. You can follow the upgrade progress by making a REST request identical to the one used to follow the progress of a configuration upgrade. See the Configuring section for more information.

Note: The upgrade process will cause all of your Elastic node processes to restart.

## Rolling Upgrades

By default, the upgrade strategy is `safe`, meaning the operator manually triggers the upgrade for each node, pausing to make sure the deployment is going as planned using `dcos elastic plan show`. The following rolling upgrade process is based on Elastic's own (recommendations for rolling upgrades)[https://www.elastic.co/guide/en/elasticsearch/reference/current/rolling-upgrades.html]. 

After starting the upgrade process, the plan will await your input to continue:
```
$ dcos elastic plan show
deploy (WAITING)
├─ master (WAITING)
│  ├─ master-0:[node] (WAITING)
│  ├─ master-1:[node] (WAITING)
│  └─ master-2:[node] (WAITING)
├─ data (WAITING)
│  ├─ data-0:[node] (WAITING)
│  └─ data-1:[node] (WAITING)
├─ ingest (WAITING)
│  └─ ingest-0:[node] (WAITING)
└─ coordinator (WAITING)
   └─ coordinator-0:[node] (WAITING)
```

Steps 1 and 2 are the same as in Elastic's guide:
1. Disable shard allocation
2. Stop non-essential indexing and perform a synced flush

Steps 3-5 are encapsulated in a single command, issued multiple times for each node and node type. First start with a master node:
```bash
dcos elastic plan continue deploy master
```

Check for the upgrade on that node to be complete using `dcos elastic plan show`. Initially it will look like this:
```
$ dcos elastic plan show deploy
deploy (IN_PROGRESS)
├─ master (IN_PROGRESS)
│  ├─ master-0:[node] (PREPARED)
│  ├─ master-1:[node] (WAITING)
│  └─ master-2:[node] (WAITING)
├─ data (WAITING)
│  ├─ data-0:[node] (WAITING)
│  └─ data-1:[node] (WAITING)
├─ ingest (WAITING)
│  └─ ingest-0:[node] (WAITING)
└─ coordinator (WAITING)
   └─ coordinator-0:[node] (WAITING)
```

When the node is successfully deployed, the plan will look like this:
```
$ dcos elastic plan show deploy
deploy (WAITING)
├─ master (WAITING)
│  ├─ master-0:[node] (COMPLETE)
│  ├─ master-1:[node] (WAITING)
│  └─ master-2:[node] (WAITING)
├─ data (WAITING)
│  ├─ data-0:[node] (WAITING)
│  └─ data-1:[node] (WAITING)
├─ ingest (WAITING)
│  └─ ingest-0:[node] (WAITING)
└─ coordinator (WAITING)
   └─ coordinator-0:[node] (WAITING)
```

Steps 6-8 are the same as in Elastic's guide:
6. Reenable shard allocation 
7. Wait for the node to recover
8. Repeat. In this case, repeat for each master node, then for each data node, then for each ingest node, and finally for each coordinator node using the `dcos elastic plan continue deploy [<phase>]` CLI command. `[<phase>]` will be one of `master`, `data`, `ingest`, `coordinator`. 

At this point, `dcos elastic plan show` should show the upgrade as complete:
```
$ dcos elastic plan show deploy
deploy (COMPLETE)
├─ master (COMPLETE)
│  ├─ master-0:[node] (COMPLETE)
│  ├─ master-1:[node] (COMPLETE)
│  └─ master-2:[node] (COMPLETE)
├─ data (COMPLETE)
│  ├─ data-0:[node] (COMPLETE)
│  └─ data-1:[node] (COMPLETE)
├─ ingest (COMPLETE)
│  └─ ingest-0:[node] (COMPLETE)
└─ coordinator (COMPLETE)
   └─ coordinator-0:[node] (COMPLETE)
```

