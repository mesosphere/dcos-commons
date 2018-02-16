---
layout: layout.pug
navigationTitle:
excerpt:
title: Troubleshooting
menuWeight: 90
---
{% assign data = site.data.services.kafka %}

{% include services/troubleshooting.md data=data %}

## Partition replication

Kafka may become unhealthy when it detects any underreplicated partitions. This error condition usually indicates a malfunctioning broker. Use the `dcos {{ data.packageName }} --name={{ data.serviceName }} topic under_replicated_partitions` and `dcos {{ data.packageName }} --name={{ data.serviceName }} topic describe <topic-name>` commands to find the problem broker and determine what actions are required.

Possible repair actions include [restarting the affected broker](#restarting-a-node) and [destructively replacing the affected broker](#replacing-a-permanently-failed-node). The replace operation is destructive and will irrevocably lose all data associated with the broker. The restart operation is not destructive and indicates an attempt to restart a broker process.

## Extending the Kill Grace Period

If the Kafka brokers are not completing the clean shutdown within the configured
`brokers.kill_grace_period` (Kill Grace Period), extend the Kill Grace Period, see [Managing - Extend the Kill Grace Period](../managing/#extend-the-kill-grace-period).
