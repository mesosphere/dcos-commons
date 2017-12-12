---
post_title: Node Settings
menu_order: 27
post_excerpt: ""
enterprise: 'no'
---

Adjust the following settings to customize the amount of resources allocated to each  node. DC/OS Apache Cassandra's [system requirements](http://cassandra.apache.org/doc/latest/operating/hardware.html) must be taken into consideration when adjusting these values. Reducing these values below those requirements may result in adverse performance and/or failures while using the service.

Each of the following settings can be customized under the **node** configuration section.

# Node Count

Customize the `Node Count` setting (default 3) under the **node** configuration section. Consult the Apache Cassandra documentation for minimum node count requirements.

*   **In DC/OS CLI options.json**: `count`: integer (default: `3`)
*   **DC/OS web interface**: `NODES`: `integer`

# CPU

You can customize the amount of CPU allocated to each node. A value of `1.0` equates to one full CPU core on a machine. Change this value by editing the **cpus** value under the **node** configuration section. Turning this too low will result in throttled tasks.

*   **In DC/OS CLI options.json**: `cpus`: number (default: `0.5`)
*   **DC/OS web interface**: `CASSANDRA_CPUS`: `number`

# Memory

You can customize the amount of RAM allocated to each node. Change this value by editing the **mem** value (in MB) under the **node** configuration section. Turning this too low will result in out of memory errors. The `heap.size` setting must also be less than this value to prevent out of memory errors resulting from the Java Virtual Machine attempting to allocate more memory than is available to the Cassandra process.

*   **In DC/OS CLI options.json**: `mem`: integer (default: `10240`)
*   **DC/OS web interface**: `CASSANDRA_MEMORY_MB`: `integer`

# JMX Port

You can customize the port that Apache Cassandra listens on for JMX requests, such as those issued by `nodetool`.

*   **In DC/OS CLI options.json**: `jmx_port`: integer (default: `7199`)
*   **DC/OS web interface**: `TASKCFG_ALL_JMX_PORT`: `integer`

# Storage Port

You can customize the port that Apache Cassandra listens on for inter-node communication.

*   **In DC/OS CLI options.json**: `storage_port`: integer (default: `7000`)
*   **DC/OS web interface**: `TASKCFG_ALL_CASSANDRA_STORAGE_PORT`: `integer`

# SSL Storage Port

You can customize the port that Apache Cassandra listens on for inter-node communication over SSL.

*   **In DC/OS CLI options.json**: `ssl_storage_port`: integer (default: `7001`)
*   **DC/OS web interface**: `TASKCFG_ALL_CASSANDRA_SSL_STORAGE_PORT`: `integer`

# Native Transport Port

You can customize the port that Apache Cassandra listens on for CQL queries.

*   **In DC/OS CLI options.json**: `native_transport_port`: integer (default: `9042`)
*   **DC/OS web interface**: `TASKCFG_ALL_CASSANDRA_NATIVE_TRANSPORT_PORT`: `integer`

# RPC Port

You can customize the port that Apache Cassandra listens on for Thrift RPC requests.

*   **In DC/OS CLI options.json**: `rpc_port`: integer (default: `9160`)
*   **DC/OS web interface**: `TASKCFG_ALL_CASSANDRA_RPC_PORT`: `integer`

# Disks

## Volume Type

The service supports two volume types:
 - `ROOT` volumes are effectively an isolated directory on the root volume, sharing IO/spindles with the rest of the host system.
 - `MOUNT` volumes are a dedicated device or partition on a separate volume, with dedicated IO/spindles.

Using `MOUNT` volumes requires [additional configuration on each DC/OS agent system](https://docs.mesosphere.com/1.9/storage/mount-disk-resources/), so the service currently uses `ROOT` volumes by default. To ensure reliable and consistent performance in a production environment, you should configure `MOUNT` volumes on the machines that will run the service in your cluster and then configure the following as `MOUNT` volumes:

To configure the disk type:
*   **In DC/OS CLI options.json**: `disk_type`: string (default: `ROOT`)
*   **DC/OS web interface**: `CASSANDRA_DISK_TYPE`: `string`

## Disk Scheduler

It is [recommended](http://docs.datastax.com/en/landing_page/doc/landing_page/recommendedSettings.html#recommendedSettings__optimizing-ssds) that you pre-configure your storage hosts to use the deadline IO scheduler in production environments.

# Placement Constraints

Placement constraints allow you to customize where Apache Cassandra nodes are deployed in the DC/OS cluster. Placement constraints support all [Marathon operators](http://mesosphere.github.io/marathon/docs/constraints.html) with this syntax: `field:OPERATOR[:parameter]`. For example, if the reference lists `[["hostname", "UNIQUE"]]`, use `hostname:UNIQUE`.

*   **In DC/OS CLI options.json**: `placement_constraint`: string (default: `""`)
*   **DC/OS web interface**: `PLACEMENT_CONSTRAINT`: `string`

## Rack-Aware Placement

Cassandra's "rack"-based fault domain support may be enabled by specifying a placement constraint that uses the `@zone` key. For example, one could spread Cassandra nodes across a minimum of three different zones/racks by specifying the constraint `@zone:GROUP_BY:3`. When a placement constraint specifying `@zone` is used, Cassandra nodes will be automatically configured with `rack`s that match the names of the zones. If no placement constraint referencing `@zone` is configured, all nodes will be configured with a default rack of `rack1`.

# Virtual networks

Cassandra supports deployment on virtual networks on DC/OS (including the `dcos` overlay network) allowing each node to have its own IP address and not use the ports resources on the agent. This can be specified by passing the following configuration during installation:
```json
{
    "service": {
        "virtual_network_enabled": true
    }
}
```
By default two nodes will not be placed on the same agent, however multiple Cassandra clusters can share an agent. As mentioned in the [developer guide](https://mesosphere.github.io/dcos-commons/developer-guide.html) once the service is deployed on a virtual network, it cannot be updated to use the host network.
