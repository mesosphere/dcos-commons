---
layout: layout.pug
navigationTitle:
excerpt:
title: Managing
menuWeight: 60
---
{% assign data = site.data.services.kafka %}

{% include services/managing.md data=data %}

# Graceful Shutdown

## Extend the Kill Grace Period

When performing a requested restart or replace of a running broker, the Kafka service will wait a default of `30` seconds for a broker to exit, before killing the process. This grace period may be customized via the `brokers.kill_grace_period` setting. In this example we will use the DC/OS CLI to increase the grace period delay to `60` seconds. This example assumes that the Kafka service instance is named `{{ data.serviceName }}`.

During the configuration update, each of the Kafka broker tasks are restarted. During the shutdown portion of the task restart, the previous configuration value for `brokers.kill_grace_period` is in effect. Following the shutdown, each broker task is launched with the new effective configuration value. Take care to monitor the amount of time Kafka brokers take to cleanly shut down by observing their logs.

Create an options file `kafka-options.json` with the following content. If you have other customized settings for your Kafka service, those settings must also be included here:

```json
{
  "brokers": {
    "kill_grace_period": 60
  }
}
```

Issue the following command:

```bash
$ dcos {{ data.packageName }} --name={{ data.serviceName }} update --options=kafka-options.json
```

## Restart a Broker with Grace

A graceful (or clean) shutdown takes longer than an ungraceful shutdown, but the next startup will be much quicker. This is because the complex reconciliation activities that would have been required are not necessary after graceful shutdown.

## Replace a Broker with Grace

The grace period must also be respected when a broker is shut down before replacement. While it is not ideal that a broker must respect the grace period even if it is going to lose persistent state, this behavior will be improved in future versions of the SDK. Broker replacement generally requires complex and time-consuming reconciliation activities at startup if there was not a graceful shutdown, so the respect of the grace kill period still provides value in most situations. We recommend setting the kill grace period only sufficiently long enough to allow graceful shutdown. Monitor the Kafka broker clean shutdown times in the broker logs to keep this value tuned to the scale of data flowing through the Kafka service.
