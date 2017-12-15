---
post_title: Service Settings
menu_order: 21
post_excerpt: ""
enterprise: 'no'
---

# Service Name

You must configure each instance of DC/OS Apache Cassandra in a given DC/OS cluster with a different service name. You can configure the service name in the **service** section of the advanced installation section of the DC/OS web interface. The default service name (used in many examples here) is `cassandra`.

*   **In DC/OS CLI options.json**: `name`: string (default: `cassandra`)
*   **DC/OS web interface**: The service name cannot be changed after the cluster has started.

# Data Center

You can configure the name of the logical data center that this Cassandra cluster runs in. This sets the `dc` property in the `cassandra-rackdc.properties` file.

*   **In DC/OS CLI options.json**: `data_center`: string (default: `dc1`)
*   **DC/OS web interface**: `CASSANDRA_LOCATION_DATA_CENTER`: `string`

# Rack

You can configure the name of the rack that this Cassandra cluster runs in. This sets the `rack` property in the `cassandra-rackdc.properties` file.

*   **In DC/OS CLI options.json**: `rack`: string (default: `rac1`)
*   **DC/OS web interface**: `CASSANDRA_LOCATION_RACK`: `string`

# Remote Seeds

You can configure the remote seeds from another Cassandra cluster that this cluster should communicate with to establish cross-data-center replication. This should be a comma-separated list of node hostnames, such as `node-0-server.cassandra.autoip.dcos.thisdcos.directory,node-1-server.cassandra.autoip.dcos.thisdcos.directory`. For more information on multi-data-center configuration, see [Configuring Multi-data-center Deployments](#configuring-multi-data-center-deployments).

*   **In DC/OS CLI options.json**: `remote_seeds`: string (default: `""`)
*   **DC/OS web interface**: `TASKCFG_ALL_REMOTE_SEEDS`: `string`

# Backup/Restore Strategy

You can configure whether the creation, transfer, and restoration of backups occurs in serial or in parallel across nodes. This option must be set to either `serial` or `parallel`. Running backups and restores in parallel has the potential to saturate your network. For this reason, we recommend that you use the default configuration for backup strategy.

*   **In DC/OS CLI options.json**: `backup_restore_strategy`: string (default: `"serial"`)
*   **DC/OS web interface**: `BACKUP_RESTORE_STRATEGY`: `string`

# Virtual networks

The Cassandra service can be run on a virtual network such as the DC/OS overlay network, affording each node its own IP address (IP per container). For details about virtual networks on DC/OS see the [documentation](/latest/networking/virtual-networks/#virtual-network-service-dns). For the Cassandra service, using a virtual network means that nodes no longer use reserved port resources on the Mesos agents.  This allows nodes to share machines with other applications that may need to use the same ports that Cassandra does. That means, however, that we cannot guarantee that the ports on the agents containing the reserved resources for Cassandra will be available, therefore we do not allow a service to change from a virtual network to the host network. **Once the service is deployed on a virtual network it must remain on that virtual network**. The only way to move your data to Cassandra on the host network is through a migration.

# TLS

The Cassandra service can be launched with TLS encryption. Enabling TLS will use TLS connections for inter-node communication between all the Cassandra nodes. Cassandra will be still available on the same configured client port (default: `9042`).

Enabling TLS is possible only in `permissive` and `strict` cluster security modes. Both modes require a [service account](https://docs.mesosphere.com/services/cassandra/cass-auth/). Additionally, a service account must have the `dcos:superuser` permission. If the permission is missing the Cassandra scheduler will not abe able to provision TLS artifacts.

*   **In DC/OS CLI options.json**: `service.security.transport_encryption.enabled`: boolean (default: `false`)
*   **DC/OS web interface**: `TASKCFG_ALL_SECURITY_TRANSPORT_ENCRYPTION_ENABLED`: `boolean`

To enable support for both TLS encrypted and non-TLS plaintext connections set the `tls_allow_plaintext` option to `true`. This option is disabled by default, so when the TLS is enabled, the non-encrypted connections would get refused.

*   **In DC/OS CLI options.json**: `service.security.transport_encryption.allow_plaintext`: boolean (default: `false`)
*   **DC/OS web interface**: `TASKCFG_ALL_SECURITY_TRANSPORT_ENCRYPTION_ALLOW_PLAINTEXT`: `boolean`

## Clients

Clients connecting to the Cassandra service are required to use [the DC/OS CA bundle](https://docs.mesosphere.com/1.10/networking/tls-ssl/get-cert/) to verify the TLS connections.

## cqlsh

TLS can be configured for `cqlsh` in the [ssl section](https://github.com/apache/cassandra/blob/652d9f64f14d8375a8412561271a7abf27722f20/conf/cqlshrc.sample#L103) of the `cqlshrc` file. Connecting over TLS requires passing the `--ssl` flag.

```sh
cqlsh --ssl [node-name] 9042
```

## TLS version and ciphers

Only [`TLS version 1.2`](https://www.ietf.org/rfc/rfc5246.txt) is supported with the following cipher suites:

* `TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256`
* `TLS_RSA_WITH_AES_128_CBC_SHA256`
* `TLS_RSA_WITH_AES_128_CBC_SHA`

For more information about TLS in the SDK see [the TLS documentation](https://mesosphere.github.io/dcos-commons/developer-guide.html#tls).
