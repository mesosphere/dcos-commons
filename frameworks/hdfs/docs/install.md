---
layout: layout.pug
navigationTitle:
excerpt:
title: Install and Customize
menuWeight: 20
---
{% assign data = site.data.services.hdfs %}

{% capture customInstallRequirements %}
- Each agent node must have 4 GB of memory and 5 GiB of disk, and each must have these ports available: 8480, 8485, 9000, 9001, 9002, 9005, and 9006, and 9007.
{% endcapture %}

{% capture customInstallConfigurations %}
### Example custom Installation

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
{% endcapture %}

{% include services/install.md
    data=data
    customInstallRequirements=customInstallRequirements
    customInstallConfigurations=customInstallConfigurations %}

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

## Clients

Clients connecting to HDFS over a TLS connection must connect to an HTTPS specific port. Each node type (`journal`, `name` and `data`) can be configured with different port numbers for TLS connections.

Clients can connect only using TLS version 1.2.

## Configuration Options

The following describes the most commonly used features of DC/OS Apache HDFS and how to configure them via the DC/OS CLI and the DC/OS GUI. There are two methods of configuring an HDFS cluster. The configuration may be specified using a JSON file during installation via the DC/OS command line (See the Installation section) or via modification to the Service Scheduler’s DC/OS environment at runtime (See the Configuration Update section). Note that some configuration options may only be specified at installation time.

### Service Configuration

The service configuration object contains some properties that MUST be specified during installation and CANNOT be modified after installation is in progress. This configuration object is similar across all DC/OS Infinity services. Service configuration example:

```json
{
  "service": {
    "name": "{{ data.serviceName }}",
    "service_account": "{{ data.serviceName }}-principal",
  }
}
```

<table class="table">
  <tr>
    <th>Property</th>
    <th>Type</th>
    <th>Description</th>
  </tr>

  <tr>
    <td>name</td>
    <td>string</td>
    <td>The name of the HDFS service installation. This must be unique for each DC/OS service instance deployed on a DC/OS cluster. It will determine the ID of the HDFS service. See [Alternate install configurations](#alternate-install-configurations) for more information on configuring the service name.</td>
  </tr>

  <tr>
    <td>service_account</td>
    <td>string</td>
    <td>The DC/OS service account for the HDFS cluster.</td>
  </tr>

</table>

### Node Configuration

The node configuration objects correspond to the configuration for nodes in the HDFS cluster. Node configuration MUST be specified during installation and MAY be modified during configuration updates. All of the properties except `disk` and `disk_type` MAY be modified during the configuration update process.

Example node configuration:
```json
  "journal_node": {
    "cpus": 0.5,
    "mem": 4096,
    "disk": 10240,
    "disk_type": "ROOT",
    "strategy": "parallel"
  },
    "name_node": {
    "cpus": 0.5,
    "mem": 4096,
    "disk": 10240,
    "disk_type": "ROOT"
  },
  "zkfc_node": {
    "cpus": 0.5,
    "mem": 4096
  },
  "data_node": {
    "count": 3,
    "cpus": 0.5,
    "mem": 4096,
    "disk": 10240,
    "disk_type": "ROOT",
    "strategy": "parallel"
  }
```

<table class="table">

  <tr>
    <th>Property</th>
    <th>Type</th>
    <th>Description</th>
  </tr>

   <tr>
    <td>cpus</td>
    <td>number</td>
    <td>The number of cpu shares allocated to the node's process.</td>
  </tr>

  <tr>
    <td>mem</td>
    <td>integer</td>
    <td>The amount of memory, in MB, allocated to the node. This value MUST be larger than the specified max heap size. Make sure to allocate enough space for additional memory used by the JVM and other overhead. A good rule of thumb is allocate twice the heap size in MB for memory.</td>
  </tr>

  <tr>
    <td>disk</td>
    <td>integer</td>
    <td>The amount of disk, in MB, allocated to node. **Note:** Once this value is configured, it can not be changed.</td>
  </tr>

  <tr>
    <td>disk_type</td>
    <td>string</td>
    <td>The type of disk to use for storing data. Possible values: <b>ROOT</b> (default) and <b>MOUNT</b>. <b>Note:</b> Once this value is configured, it can not be changed.
    <ul>
    <li><b>ROOT:</b> Data is stored on the same volume as the agent work directory and the node tasks use the configured amount of <i>disk</i> space.</li>
    <li><b>MOUNT:</b> Data will be stored on a dedicated, operator-formatted volume attached to the agent. Dedicated MOUNT volumes have performance advantages and a disk error on these MOUNT volumes will be correctly reported to HDFS.</li>
    </ul>
    </td>
  </tr>

  <tr>
    <td>strategy</td>
    <td>string</td>
    <td>The strategy used to deploy that node type. Possible values: <b>parallel</b> (default) and <b>serial</b>.
    <ul>
    <li><b>parallel:</b> All nodes of that type are deployed at the same time.</li>
    <li><b>serial:</b> All nodes of that type are deployed in sequence.</li>
    </ul>
    </td>
  </tr>

  <tr>
    <td>count</td>
    <td>integer</td>
    <td>The number of nodes of that node type for the cluster. There are always exactly two name nodes, so the name_node object has no count property. Users may select either 3 or 5 journal nodes. The default value of 3 is sufficient for most deployments and should only be overridden after careful thought. At least 3 data nodes should be configured, but this value may be increased to meet the storage needs of the deployment.</td>
  </tr>
</table>

### HDFS File System Configuration

The HDFS file system network configuration, permissions, and compression is configured via the `hdfs` JSON object. Once these properties are set at installation time they can not be reconfigured.
Example HDFS configuration:

```json
{
  "hdfs": {
    "name_node_rpc_port": 9001,
    "name_node_http_port": 9002,
    "journal_node_rpc_port": 8485,
    "journal_node_http_port": 8480,
    "data_node_rpc_port": 9005,
    "data_node_http_port": 9006,
    "data_node_ipc_port": 9007,
    "permissions_enabled": false,
    "name_node_heartbeat_recheck_interval": 60000,
    "compress_image": true,
    "image_compression_codec": "org.apache.hadoop.io.compress.SnappyCodec"
  }
}
```

<table class="table">

  <tr>
    <th>Property</th>
    <th>Type</th>
    <th>Description</th>
  </tr>

   <tr>
    <td>name_node_rpc_port</td>
    <td>integer</td>
    <td>The port on which the name nodes will listen for RPC connections.</td>
  </tr>

   <tr>
    <td>name_node_http_port</td>
    <td>integer</td>
    <td>The port on which the name nodes will listen for HTTP connections.</td>
  </tr>

   <tr>
    <td>journal_node_rpc_port</td>
    <td>integer</td>
    <td>The port on which the journal nodes will listen for RPC connections.</td>
  </tr>

   <tr>
    <td>journal_node_http_port</td>
    <td>integer</td>
    <td>The port on which the journal nodes will listen for HTTP connections.</td>
  </tr>

   <tr>
    <td>data_node_rpc_port</td>
    <td>integer</td>
    <td>The port on which the data nodes will listen for RPC connections.</td>
  </tr>

   <tr>
    <td>data_node_http_port</td>
    <td>integer</td>
    <td>The port on which the data nodes will listen for HTTP connections.</td>
  </tr>

   <tr>
    <td>data_node_ipc_port</td>
    <td>integer</td>
    <td>The port on which the data nodes will listen for IPC connections. This property is useful if you deploy a service that colocates with HDFS data nodes. It provides domain socket communication instead of RPC</td>
  </tr>
</table>

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
