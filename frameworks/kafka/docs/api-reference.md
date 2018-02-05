---
layout: layout.pug
navigationTitle:
excerpt:
title: API Reference
menuWeight: 70
---

{% include services/api-reference.md
    techName="Apache Kafka"
    packageName="beta-kafka"
    serviceName="kafka" %}

# Connection Information

Kafka comes with many useful tools of its own that often require either Zookeeper connection information or the list of broker endpoints. This information can be retrieved in an easily consumable format from the `/endpoints` endpoint:

```bssh
$ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/endpoints/broker"
{
  "vip": "broker.kafka.l4lb.thisdcos.directory:9092",
  "address": [
    "10.0.0.35:1028",
    "10.0.1.249:1030"
  ],
  "dns": [
    "kafka-0-broker.kafka.autoip.dcos.thisdcos.directory:1028",
    "kafka-1-broker.kafka.autoip.dcos.thisdcos.directory:1030"
  ],
}
```

The same information can be retrieved through the DC/OS CLI:

```bash
$ dcos betai-kafka endpoints broker
{
  "vip": "broker.kafka.l4lb.thisdcos.directory:9092",
  "address": [
    "10.0.0.35:1028",
    "10.0.1.249:1030"
  ],
  "dns": [
    "kafka-0-broker.kafka.autoip.dcos.thisdcos.directory:1028",
    "kafka-1-broker.kafka.autoip.dcos.thisdcos.directory:1030"
  ],
}
```

# Topic Operations

These operations mirror what is available with `bin/kafka-topics.sh`.

## List Topics

```bash
$ dcos beta-kafka --name=kafka topic list
[
  "topic1",
  "topic0"
]
```

```bash
$ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics"
[
  "topic1",
  "topic0"
]
```

## Describe Topic

```bash
$ dcos beta-kafka --name=kafka topic describe topic1
{
  "partitions": [
  {
    "0": {
      "controller_epoch": 1,
      "isr": [
        0,
        1,
        2
      ],
      "leader": 0,
      "leader_epoch": 0,
      "version": 1
    }
  },
  {
    "1": {
      "controller_epoch": 1,
      "isr": [
        1,
        2,
        0
      ],
      "leader": 1,
      "leader_epoch": 0,
      "version": 1
    }
  },
  {
    "2": {
      "controller_epoch": 1,
      "isr": [
        2,
        0,
        1
      ],
      "leader": 2,
      "leader_epoch": 0,
      "version": 1
    }
  }
  ]
}
```

```bash
$ curl -X POST -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/topic1"
{
  "partitions": [
  {
    "0": {
      "controller_epoch": 1,
      "isr": [
        0,
        1,
        2
      ],
      "leader": 0,
      "leader_epoch": 0,
      "version": 1
    }
  },
  {
    "1": {
      "controller_epoch": 1,
      "isr": [
        1,
        2,
        0
      ],
      "leader": 1,
      "leader_epoch": 0,
      "version": 1
    }
  },
  {
    "2": {
      "controller_epoch": 1,
      "isr": [
        2,
        0,
        1
      ],
      "leader": 2,
      "leader_epoch": 0,
      "version": 1
    }
  }
  ]
}

```

## Create Topic

```bash
$ dcos beta-kafka --name=kafka topic create topic1 --partitions=3 --replication=3
{
  "message": "Output: Created topic \"topic1\"\n"
}
```

```bash
$ curl -X POST -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/topic1?partitions=3&replication=3"
{
  "message": "Output: Created topic \"topic1\"\n"
}
```

## View Topic Offsets

There is an optional `--time` parameter which may be set to either "first", "last", or a timestamp in milliseconds as [described in the Kafka documentation][15].

```bash
$ dcos beta-kafka --name=kafka topic offsets topic1 --time=last
[
  {
    "2": "334"
  },
  {
    "1": "333"
  },
  {
    "0": "333"
  }
]
```

```bash
$ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/topic1/offsets?time=-1"
[
  {
    "2": "334"
  },
  {
    "1": "333"
  },
  {
    "0": "333"
  }
]
```

## Alter Topic Partition Count

```
$ dcos beta-kafka --name=kafka topic partitions topic1 2
{
  "message": "Output: WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affectednAdding partitions succeeded!n"
}
```

```bash
$ curl -X PUT -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/topic1?operation=partitions&partitions=2"
{
  "message": "Output: WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affectednAdding partitions succeeded!n"
}
```

## Run Producer Test on Topic

```
$ dcos beta-kafka --name=kafka topic producer_test topic1 10
{
  "message": "10 records sent, 70.422535 records/sec (0.07 MB/sec), 24.20 ms avg latency, 133.00 ms max latency, 13 ms 50th, 133 ms 95th, 133 ms 99th, 133 ms 99.9th.n"
}

```bash
$ curl -X PUT -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/topic1?operation=producer-test&messages=10"
{
  "message": "10 records sent, 70.422535 records/sec (0.07 MB/sec), 24.20 ms avg latency, 133.00 ms max latency, 13 ms 50th, 133 ms 95th, 133 ms 99th, 133 ms 99.9th.n"
}
```

This runs the equivalent of the following command from the machine running the Kafka Scheduler:
```bash
$ kafka-producer-perf-test.sh \
  --topic <topic> \
  --num-records <messages> \
  --throughput 100000 \
  --record-size 1024 \
  --producer-props bootstrap.servers=<current broker endpoints>
```

## Delete Topic

```bash
$ dcos beta-kafka --name=kafka topic delete topic1
{
  "message": "Topic topic1 is marked for deletion.nNote: This will have no impact if delete.topic.enable is not set to true.n"
}
```

```bash
$ curl -X DELETE -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/topic1"
{
  "message": "Topic topic1 is marked for deletion.nNote: This will have no impact if delete.topic.enable is not set to true.n"
}
```

Note the warning in the output from the commands above. You can change the indicated "delete.topic.enable" configuration value as a configuration change.

## List Under Replicated Partitions

```bash
$ dcos beta-kafka --name=kafka topic under_replicated_partitions
{
  "message": ""
}
```

```bash
$ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/under_replicated_partitions"
{
  "message": ""
}
```

## List Unavailable Partitions

```bash
$ dcos beta-kafka --name=kafka topic unavailable_partitions
{
  "message": ""
}
```

```bash
$ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/unavailable_partitions"
{
  "message": ""
}
```
