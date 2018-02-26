---
layout: layout.pug
navigationTitle:
excerpt:
title: Install and Customize
menuWeight: 20
---
{% assign data = site.data.services.hdfs %}

{% include services/install1.md data=data %}

### Example custom installation

If you are ready to ship into production, you will likely need to customize the deployment to suit the workload requirements of your application(s). Customize the default deployment by creating a JSON file, then pass it to `dcos package install` using the `--options` parameter.

Sample JSON options file named `sample-{{ data.serviceName }}.json`:

```json
{
  "data_node": {
    "count": 10
  }
}
```

This cluster will have 10 data nodes instead of the default value of 3.
See the Configuration section for a list of fields that can be customized via a options JSON file when the HDFS cluster is created.

### Minimal installation

Many of the other Infinity services currently support deployment in DC/OS Docker for local testing a local machine. However, DC/OS HDFS currently only supports deployment with an HA name service managed by a Quorum Journal. The resource requirements for such a deployment make it prohibitive to install on a local development machine. The default deployment, is the minimal safe deployment for a DC/OS HDFS cluster. Community contributions to support deployment of a non-HA cluster, e.g. a single name node and data node with no failure detector, would be welcome.

{% include services/install2.md data=data %}

## Topology

An individual HDFS deployment will colocate name nodes with journal nodes, but it will not colocate two name nodes or two journal nodes on the same agent node in the cluster. Data nodes may be colocated with both name nodes and/or journal nodes. If multiple HDFS clusters are installed, they may share the same agent nodes in the cluster provided that no ports specified in the service configurations conflict for those node types.

## Initial Deployment Strategy

HDFS deployment occurs in a series of Phases which are configured in the HDFS service's Deploy Plan.

The `deploy` plan may be viewed either using the DC/OS CLI or directly over HTTP. See the [API Reference](api-reference.md#view-plan) for more information on viewing the progress of the `deploy` plan.

### Quorum Journal

The first phase of the installation is the Quorum Journal phase. This phase will deploy three journal nodes to provide a Quorum Journal for the HA name service. Each step in the phase represents an individual journal node.

### Name Service

The second phase of the installation is deployment of the HA name service. This phase deploys two name nodes.  Needed format and bootstrap operations occur as necessary.

### ZKFC

The third phase of the installation is deployment of the ZKFC nodes. This phase deploys two ZKFC nodes to enable ZooKeeper failure detection. Each step represents an individual ZKFC node, and there are always exactly two.

### Distributed Storage

The fourth and final phase of the installation is deployment of the distributed storage service. This phase deploys the data nodes that are configured to act as storage for the cluster. The number of data nodes can be reconfigured post installation.

## Configuration Deployment Strategy

This configuration update strategy is analogous to the installation procedure above. If the configuration update is accepted, there will be no errors in the generated `deploy` plan, and a rolling restart will be performed on all affected nodes to apply the updated configuration.

## Configuration Options

The following describes the most commonly used features of DC/OS Apache HDFS and how to configure them via the DC/OS CLI and the DC/OS GUI. There are two methods of configuring an HDFS cluster. The configuration may be specified using a JSON file during installation via the DC/OS command line (See the Installation section) or via modification to the Service Scheduler’s DC/OS environment at runtime (See the Configuration Update section). Note that some configuration options may only be specified at installation time.

### Service Configuration

The service configuration object contains some properties that MUST be specified during installation and CANNOT be modified after installation is in progress.

For more information on service configuration see [the documentation on configuring a service from the catalog](https://docs.mesosphere.com/latest/deploying-services/config-universe-service/).

### Node Configuration

The node configuration objects correspond to the configuration for nodes in the HDFS cluster. Node configuration MUST be specified during installation and MAY be modified during configuration updates. All of the properties except `disk` and `disk_type` MAY be modified during the configuration update process.

#### A Note on Memory Configuration

As part of the service (or node) configuration, the amount of memory in MB allocated to the node can be specified. This value *must& be larger than the specified maximum heap size. Make sure to allocate enough space for additional memory used by the JVM and other overhead. A good rule of thumb is allocate twice as much memory as the size of the heap.

#### A Note on Disk Types

As already noted, the disk size and type specifications cannot be modified after initial installation. Furthermore, the following disk volume types are available:

* `ROOT`: Data is stored on the same volume as the agent work directory and the node tasks use the configured amount of disk space.
* `MOUNT`: Data will be stored on a dedicated, operator-formatted volume attached to the agent. Dedicated MOUNT volumes have performance advantages and a disk error on these MOUNT volumes will be correctly reported to HDFS.

### HDFS File System Configuration

The HDFS file system network configuration, permissions, and compression are configured via the `hdfs` JSON object. Once these properties are set at installation time they can not be reconfigured.

### Operating System Configuration

In order for HDFS to function correctly, you must perform several important configuration modifications to the OS hosting the deployment. HDFS requires OS-level configuration settings typical of a production storage server.

<table class="table">

  <tr>
    <th>File</th>
    <th>Setting</th>
    <th>Value</th>
    <th>Reason</th>
  </tr>

   <tr>
    <td>/etc/sysctl.conf</td>
    <td>vm.swappiness</td>
    <td>0</td>
    <td>If the OS swaps out the HDFS processes, they can fail to respond to RPC requests, resulting in the process being marked down by the cluster. This can be particularly troublesome for name nodes and journal nodes.</td>
  </tr>

  <tr>
    <td>/etc/security/limits.conf</td>
    <td>nofile</td>
    <td>unlimited</td>
    <td>If this value is too low, a job that operate on the HDFS cluster may fail due to too may open file handles.</td>
  </tr>

  <tr>
    <td>/etc/security/limits.conf, /etc/security/limits.d/90-nproc.conf</td>
    <td>nproc</td>
    <td>32768</td>
    <td>An HDFS node spawns many threads, which go towards kernel nproc count. If nproc is not set appropriately, the node will be killed.</td>
  </tr>

</table>
