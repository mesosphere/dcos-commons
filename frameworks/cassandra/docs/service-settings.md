---
post_title: Service Settings
nav_title: Service Settings
menu_order: 30
post_excerpt: ""
feature_maturity: preview
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

# Overlay networks
The Cassandra service can be run on the DC/OS overlay network, affording each node its own IP address (IP per container). For details about virtual networks on DC/OS see the [documentation](/latest/networking/virtual-networks/#virtual-network-service-dns). For the Cassandra service, using the overlay network means that nodes no longer use reserved port resources on the Mesos agents.  This means that nodes to share machines with other applications that may need to use the same ports that Cassandra does. That means, however, that we cannot guarantee that the ports on the agents containing the reserved resources for Cassandra will be available, therefore we do not allow a service to change from the overlay network to the host network. **Once the service is deployed on the overlay network it must remain on the overlay network**. The only way to move your data to Cassandra on the host network is through a migration.  
