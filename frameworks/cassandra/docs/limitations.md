---
layout: layout.pug
navigationTitle:
excerpt:
title: Limitations
menuWeight: 100
---
{% assign data = site.data.services.cassandra %}

{% include services/limitations.md data=data %}

## Backup/Restore

The service does not support performing backup and restore with authentication/authorization enabled in this or previous versions.

## Node Count

The DC/OS {{ data.techName }} Service must be deployed with at least 3 nodes.

## Security

{{ data.techName }}'s native authentication, and authorization mechanisms are not supported at this time.

### Toggling Transport Encryption

Transport encryption using TLS can be toggled (enabled / disabled), but will trigger a rolling restart of the cluster. As each node restarts, a client may lose connectivity based on its security settings and the value of the `service.security.transport_encryption.allow_plaintext` configuration option. It is recommended that backups are made and downtime is scheduled.

In order to enable TLS, a service account and corresponding secret is required. Since it is not possible to change the service account used by a service, it is recommended that the service is deployed with an explicit service account to allow for TLS to be enabled at a later stage.

## Data Center Name

The name of the data center cannot be changed after installation. `service.data_center` and `service.rack` options are not allowed to be modified once {{ data.techName }} is installed.

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
