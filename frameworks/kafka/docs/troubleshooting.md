---
layout: layout.pug
navigationTitle:
excerpt:
title: Troubleshooting
menuWeight: 90

packageName: beta-kafka
serviceName: kafka
---

{% include services/troubleshooting.md
    packageName=page.packageName
    serviceName=page.serviceName %}

## Partition replication

Kafka may become unhealthy when it detects any underreplicated partitions. This error condition usually indicates a malfunctioning broker. Use the `dcos {{ page.packageName }} --name={{ page.serviceName }} topic under_replicated_partitions` and `dcos {{ page.packageName }} --name={{ page.serviceName }} topic describe <topic-name>` commands to find the problem broker and determine what actions are required.

Possible repair actions include [restarting the affected broker](#restarting-a-node) and [destructively replacing the affected broker](#replacing-a-permanently-failed-node). The replace operation is destructive and will irrevocably lose all data associated with the broker. The restart operation is not destructive and indicates an attempt to restart a broker process.

## Extending the Kill Grace Period

If the Kafka brokers are not completing the clean shutdown within the configured
`brokers.kill_grace_period` (Kill Grace Period), extend the Kill Grace Period, see [Managing - Extend the Kill Grace Period](managing.md#extend-the-kill-grace-period).
