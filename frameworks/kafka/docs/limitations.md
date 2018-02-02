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
