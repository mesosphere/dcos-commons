---
layout: layout.pug
navigationTitle:
excerpt:
title: Managing
menuWeight: 60
---

{% include services/managing.md
    podType="data"
    taskType="node"
    techName="HDFS"
    packageName="beta-hdfs"
    serviceName="hdfs"
    cliPackageName="beta-hdfs --name=hdfs" %}

# Replacing Journal Nodes

The following section describes how to perform a `replace` of a Journal Node. This guide uses Journal Node 0 to
refer to the unhealthy Journal Node as it's the replaced Journal Node.

## Replace Command

Replace the Journal Node via:
```bash
$ dcos beta-hdfs pod replace journal-0
```

## Detecting an unhealthy Journal Node after `replace`

Once the replaced Journal Node is up and running, you should see the following in the `stderr` log:
```
org.apache.hadoop.hdfs.qjournal.protocol.JournalNotFormattedException: Journal Storage Directory
```

This indicates this Journal Node is unhealthy.

## Determining a healthy Journal Node

From the non-replaced Journal Nodes, confirm that a Journal Node is healthy:
  - Inspect the `stderr` log and check for absence of errors.
  - In `journal-data/hdfs/current`, check for:
    - consecutive `edits_xxxxx-edits_xxxxx` files with timestamps between each differing by ~2 minutes.
    - An `edits_inprogess_` file modified within the past 2 minutes.

Once identified, make a note of which Journal Node is healthy.

## Fixing the unhealthy Journal Node

1. SSH into the sandbox of the unhealthy Journal Node via
```bash
$ dcos task exec -it journal-0 /bin/bash
```

2. In this sandbox, create the directory `journal-data/hdfs/current`:
```bash
$ mkdir -p journal-data/hdfs/current
```

3. From the healthy Journal Node identified previously, copy the contents of the `VERSION` file into `journal-data/hdfs/current/VERSION`.

4. On the unhealthy Journal Node, create a file with the same path as the `VERSION` file on the healthy Journal Node:
`journal-data/hdfs/current/VERSION`. Paste the copied contents into this file.

5. Restart the unhealthy Journal Node via:
```bash
$ dcos beta-hdfs pod restart journal-0
```

6. Once the restarted Journal Node is up and running, confirm that it is now healthy again by inspecting the `stderr` log. You should see:
```bash
INFO namenode.FileJournalManager: Finalizing edits file
```
