---
layout: layout.pug
navigationTitle:
excerpt:
title: Limitations
menuWeight: 100
---

{% include services/limitations.md %}

## Zones

DC/OS Zones allow the service to implement rack-awareness. When the service is deployed with some zone configuration (or lack thereof), it cannot be upgraded/downgraded to another zone configuration.
