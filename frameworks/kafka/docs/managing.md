---
layout: layout.pug
navigationTitle:
excerpt:
title: Managing
menuWeight: 60
---

{% include services/managing.md
    podType="kafka"
    taskType="broker"
    techName="Apache Kafka"
    packageName="beta-kafka"
    serviceName="kafka" %}

# Graceful Shutdown

## Extend the Kill Grace Period

Increase the `brokers.kill_grace_period` value via the DC/OS CLI, i.e.,  to `60`
seconds. This example assumes that the Kafka service instance is named `kafka`.

During the configuration update, each of the Kafka broker tasks are restarted. During
the shutdown portion of the task restart, the previous configuration value for
`brokers.kill_grace_period` is in effect. Following the shutdown, each broker
task is launched with the new effective configuration value. Take care to monitor
the amount of time Kafka brokers take to cleanly shutdown. Find the relevant log
entries in the [Configure](configure.md) section.

Create an options file `kafka-options.json` with the following content:

```json
{
  "brokers": {
    "kill_grace_period": 60
  }
}
```

Issue the following command:

```bash
$ dcos beta-kafka --name=/kafka update --options=kafka-options.json
```

## Restart a Broker with Grace

A graceful (or clean) shutdown takes longer than an ungraceful shutdown, but the next startup will be much quicker. This is because the complex reconciliation activities that would have been required are not necessary after graceful shutdown.

## Replace a Broker with Grace

The grace period must also be respected when a broker is shut down before replacement. While it is not ideal that a broker must respect the grace period even if it is going to lose persistent state, this behavior will be improved in future versions of the SDK. Broker replacement generally requires complex and time-consuming reconciliation activities at startup if there was not a graceful shutdown, so the respect of the grace kill period still provides value in most situations. We recommend setting the kill grace period only sufficiently long enough to allow graceful shutdown. Monitor the Kafka broker clean shutdown times in the broker logs to keep this value tuned to the scale of data flowing through the Kafka service.
