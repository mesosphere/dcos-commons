---
layout: layout.pug
navigationTitle:
excerpt:
title: Managing
menuWeight: 60
---

{% include services/managing.md
    pod_type="data"
    task_type="node"
    tech_name="Elastic"
    package_name="beta-elastic"
    service_name="elastic"
    cli_package_name="beta-elastic --name=elastic" %}

# Add a Data/Ingest/Coordinator Node

Increase the `DATA_NODE_COUNT`/`INGEST_NODE_COUNT`/`COORDINATOR_NODE_COUNT` value from the DC/OS dashboard as described in the Configuring section. This creates an update plan as described in that section. An additional node will be added as the last step of that plan.
