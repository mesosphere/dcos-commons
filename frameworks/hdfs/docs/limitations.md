---
layout: layout.pug
navigationTitle:
excerpt:
title: Limitations
menuWeight: 100
---
{% assign data = site.data.services.hdfs %}

{% include services/limitations.md data=data %}

## Zones

DC/OS Zones allow the service to implement rack-awareness. When the service is deployed with some zone configuration (or lack thereof), it cannot be upgraded/downgraded to another zone configuration.

<!-- TBD? ## Security

### Transport Encryption

### Kerberos -->
