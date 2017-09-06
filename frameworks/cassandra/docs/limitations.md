---
post_title: Limitations
menu_order: 80
feature_maturity: preview
enterprise: 'no'
---

## Node Count

The DC/OS Cassandra Service must be deployed with at least 3 nodes.

## Rack-Aware Placement

Apache Cassandra's Rack-Aware Replication is not supported at this time.

## Security

Apache Cassandra's native TLS, authentication, and authorization features are not supported at this time.

## Virtual networks

When the DC/OS Cassandra Service is deployed on a virtual network, the service may not be switched to host networking without a full re-installation. The same is true for attempting to switch from host to virtual networking.

## Data Center Name

Data Center Name:

The name of the data center cannot be changed after installation. `service.data_center` and `service.rack` options are not allowed to be modified once Cassandra is installed. 

```
"service": {
……
      data_center": {
          "description": "The name of the data center this cluster is running in",
          "type": "string",
          "default": "datacenter1"
        },
        "rack": {
          "description": "The name of the rack this cluster is running on",
          "type": "string",
          "default": "rack1"
        },
……
}
```
