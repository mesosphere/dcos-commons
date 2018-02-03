---
layout: layout.pug
navigationTitle:
excerpt:
title: Limitations
menuWeight: 100
---

{% include services/limitations.md %}

## Backup/Restore

The service does not support performing backup and restore with authentication/authorization enabled in this or previous versions.

## Node Count

The DC/OS Cassandra Service must be deployed with at least 3 nodes.

## Security

Apache Cassandra's native TLS, authentication, and authorization features are not supported at this time.

## Data Center Name

The name of the data center cannot be changed after installation. `service.data_center` and `service.rack` options are not allowed to be modified once Cassandra is installed.

```
"service": {
...
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
...
}
```

