---
layout: layout.pug
navigationTitle:
excerpt:
title: Limitations
menuWeight: 100
---

{% include services/limitations.md %}

## Configurations

The "disk" configuration value is denominated in MB. We recommend you set the configuration value `log_retention_bytes` to a value smaller than the indicated "disk" configuration. See the Configuring section for instructions for customizing these values.

## Security

### Kerberos

When Kerberos is enabled, the broker VIP is disabled as Kerberized clients will not be able to use it. This is because each Kafka broker uses a specific Kerberos principal and cannot accept connections from a single unified principal which the VIP would require.

### Toggling Kerberos

Kerberos authentication can be toggled (enabled / disabled), but this triggers a rolling restart of the cluster. Clients configured with the old security settings will lose connectivity during and after this process. It is recommended that backups are made and downtime is scheduled.

### Toggling TLS

Transport encryption using TLS can be toggled (enabled / disabled), but will trigger a rolling restart of the cluster. As each broker restarts, a client may lose connectivity based on its security settings and the value of the `service.security.transport_encryption.allow_plaintext` configuration option. It is recommended that backups are made and downtime is scheduled.

In order to enable TLS, a service account and corresponding secret is required. Since it is not possible to change the service account used by a service, it is recommended that the service is deployed with an explicit service account to allow for TLS to be enabled at a later stage.
