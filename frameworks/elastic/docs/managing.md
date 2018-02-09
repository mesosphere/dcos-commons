---
layout: layout.pug
navigationTitle:
excerpt:
title: Managing
menuWeight: 60
---
{% assign data = site.data.services.elastic %}

{% include services/managing.md data=data %}

# Add a Data/Ingest/Coordinator Node

Increase the `DATA_NODE_COUNT`/`INGEST_NODE_COUNT`/`COORDINATOR_NODE_COUNT` value from the DC/OS dashboard as described in the Configuring section. This creates an update plan as described in that section. An additional node will be added as the last step of that plan.
