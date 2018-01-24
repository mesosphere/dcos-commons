---
layout: layout.pug
navigationTitle: 
excerpt:
title: API Reference
menuWeight: 70

---


<a name="#rest-auth"></a>
# REST API Authentication
REST API requests must be authenticated. This authentication is only applicable for interacting with the Kafka REST API directly. You do not need the token to access the Kafka nodes themselves.

If you are using Enterprise DC/OS, follow these instructions to [create a service account and an authentication token](https://docs.mesosphere.com/1.9/security/service-auth/custom-service-auth/). You can then configure your service to automatically refresh the authentication token when it expires. To get started more quickly, you can also [get the authentication token without a service account](https://docs.mesosphere.com/1.9/security/iam-api/), but you will need to manually refresh the token.

If you are using open source DC/OS, follow these instructions to [pass your authentication token to the DC/OS endpoint](https://dcos.io/docs/1.9/security/iam-api/).

Once you have the authentication token, you can store it in an environment variable and reference it in your REST API calls:

```bash
$ export auth_token=uSeR_t0k3n
```

The `curl` examples in this document assume that an auth token has been stored in an environment variable named `auth_token`.

If you are using Enterprise DC/OS, the security mode of your installation may also require the `--ca-cert` flag when making REST calls. Refer to [Obtaining and passing the DC/OS certificate in cURL requests](https://docs.mesosphere.com/1.9/networking/tls-ssl/#get-dcos-cert) for information on how to use the `--cacert` flag. [If your security mode is `disabled`](https://docs.mesosphere.com/1.9/networking/tls-ssl/), do not use the `--ca-cert` flag.

For ongoing maintenance of the Kafka cluster itself, the Kafka service exposes an HTTP API whose structure is designed to roughly match the tools provided by the Kafka distribution, such as `bin/kafka-topics.sh`.

The examples here provide equivalent commands using both the [DC/OS CLI](https://github.com/mesosphere/dcos-cli) (with the `kafka` CLI module installed) and `curl`. These examples assume a service named `kafka` (the default), and the `curl` examples assume that the DC/OS cluster path has been stored in an environment variable `$dcos_url`. Replace these with appropriate values as needed.

The `dcos beta-kafka` CLI commands have a `--name` argument, allowing the user to specify which Kafka instance to query. The value defaults to `kafka`, so it's technically redundant to specify `--name=kafka` in these examples.

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

# Broker Operations

## List All Brokers

```bash
$ dcos beta-kafka --name=kafka pod list
[
  "kafka-0",
  "kafka-1",
  "kafka-2"
]
```

```bash
$ curl -H "Authorization: token=$auth_token" "$dcos_url/service/kafka/v1/pod"
[
  "kafka-0",
  "kafka-1",
  "kafka-2"
]
```

## Restart a Single Broker

Restarts the broker in-place.

```bash
$ dcos beta-kafka --name=kafka pod restart kafka-1
[
  "pod": "kafka-1",
  "tasks": ["kafka-1-broker"]
]
```

```bash
$ curl -X PUT -H "Authorization: token=$auth_token" "$dcos_url/service/kafka/v1/pod/kafka-1/restart"
[
  "pod": "kafka-1",
  "tasks": ["kafka-1-broker"]
]
```

## Replace a Single Broker

Restarts the broker and replaces its existing resource/volume allocations. The new broker instance may also be placed on a different machine.

```bash
$ dcos beta-kafka --name=kafka pod replace kafka-1
[
  "pod": "kafka-1",
  "tasks": ["kafka-1-broker"]
]
```

```bash
$ curl -X PUT -H "Authorization: token=$auth_token" "$dcos_url/service/kafka/v1/pod/kafka-1/replace"
[
  "pod": "kafka-1",
  "tasks": ["kafka-1-broker"]
]
```

## Pause a Broker

The pause endpoint can be used to relaunch a node in an idle command state for debugging purposes.

CLI example
```
dcos beta-kafka debug pod pause <node-id>
```

HTTP Example
```bash
$ curl -X POST -H "Authorization:token=$auth_token" <dcos_url>/service/kafka/v1/pod/<node-id>/pause
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
$ curl -H "Authorization: token=$auth_token" "$dcos_url/service/kafka/v1/topics"
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
$ curl -X POST -H "Authorization: token=$auth_token" "$dcos_url/service/kafka/v1/topics/topic1?partitions=3&replication=3"
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
$ curl -H "Authorization: token=$auth_token" "$dcos_url/service/kafka/v1/topics/topic1/offsets?time=-1"
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

# Service Status

Send a GET request to the `/v1/state/properties/suppressed` endpoint to learn if Kafka is in a `suppressed` state and not receiving offers. If a service does not need offers, Mesos can "suppress" it so that other services are not starved for resources.

You can use this request to troubleshoot: if you think Kafka should be receiving resource offers, but is not, you can use this API call to see if Kafka is suppressed. You will receive a response of `true` or `false`.

```bash
$curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/state/properties/suppressed"
```

# Config History

These operations relate to viewing the service's configuration history.

## List Configuration IDs

```bash
$ dcos beta-kafka --name=kafka config list
[
  "319ebe89-42e2-40e2-9169-8568e2421023",
  "294235f2-8504-4194-b43d-664443f2132b"
]
```

```bash
$ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/configurations"
[
  "319ebe89-42e2-40e2-9169-8568e2421023",
  "294235f2-8504-4194-b43d-664443f2132b"
]
```

## Describe Configuration

This configuration shows a default per-broker memory allocation of 2048 (configured via the `BROKER_MEM` parameter):

```bash
$ dcos beta-kafka --name=kafka config show 319ebe89-42e2-40e2-9169-8568e2421023
```

Since the configuration resource is output for several CLI and API usages, a single reference version of this resource
is provided in [Appendix A](.#appendix-a-configuration-resource).

The equivalent DC/OS API resource request follows:

```bash
$ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/configurations/319ebe89-42e2-40e2-9169-8568e2421023"
```

The CLI output for viewing a specific configuration matches the API output.

## Describe Target Configuration

The target configuration, meanwhile, shows an increase of configured per-broker memory from 2048 to 4096 (again, configured as `BROKER_MEM`):

```bash
$ dcos beta-kafka --name=kafka config target
```

Since the configuration resource is output for several CLI and API usages, a single reference version of this resource
is provided in Appendix A.

```bash
$ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/configurations/target"
```

The CLI output for viewing a specific configuration matches the API output.

# Config Updates

These options relate to viewing and controlling rollouts and configuration updates.

## View Plan Status

Displays all Phases and Steps in the service Plan. If a rollout is currently in progress, this returns a 503 HTTP code with response content otherwise unchanged.

```bash
$ dcos beta-kafka --name=kafka plan show deploy --json
{
  "phases": [
    {
      "id": "1915bcad-1235-400f-8406-4ac7555a7d34",
      "name": "Reconciliation",
      "steps": [
        {
          "id": "9854a67d-7803-46d0-b278-402785fe3199",
          "status": "COMPLETE",
          "name": "Reconciliation",
          "message": "Reconciliation complete"
        }
      ],
      "status": "COMPLETE"
    },
    {
      "id": "3e72c258-1ead-465f-871e-2a305d29124c",
      "name": "Update to: 329ef254-7331-48dc-a476-8a0e45752871",
      "steps": [
        {
          "id": "ebf4cb02-1011-452a-897a-8c4083188bb2",
          "status": "COMPLETE",
          "name": "broker-0",
          "message": "Broker-0 is COMPLETE"
        },
        {
          "id": "ff9e74a7-04fd-45b7-b44c-00467aaacd5b",
          "status": "IN_PROGRESS",
          "name": "broker-1",
          "message": "Broker-1 is IN_PROGRESS"
        },
        {
          "id": "a2ba3969-cb18-4a05-abd0-4186afe0f840",
          "status": "PENDING",
          "name": "broker-2",
          "message": "Broker-2 is PENDING"
        }
      ],
      "status": "IN_PROGRESS"
    }
  ],
  "errors": [],
  "status": "IN_PROGRESS"
}
```

```bash
$ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/plan/deploy"
{
  "phases": [
    {
      "id": "1915bcad-1235-400f-8406-4ac7555a7d34",
      "name": "Reconciliation",
      "steps": [
        {
          "id": "9854a67d-7803-46d0-b278-402785fe3199",
          "status": "COMPLETE",
          "name": "Reconciliation",
          "message": "Reconciliation complete"
        }
      ],
      "status": "COMPLETE"
    },
    {
      "id": "3e72c258-1ead-465f-871e-2a305d29124c",
      "name": "Update to: 329ef254-7331-48dc-a476-8a0e45752871",
      "steps": [
        {
          "id": "ebf4cb02-1011-452a-897a-8c4083188bb2",
          "status": "COMPLETE",
          "name": "broker-0",
          "message": "Broker-0 is COMPLETE"
        },
        {
          "id": "ff9e74a7-04fd-45b7-b44c-00467aaacd5b",
          "status": "IN_PROGRESS",
          "name": "broker-1",
          "message": "Broker-1 is IN_PROGRESS"
        },
        {
          "id": "a2ba3969-cb18-4a05-abd0-4186afe0f840",
          "status": "PENDING",
          "name": "broker-2",
          "message": "Broker-2 is PENDING"
        }
      ],
      "status": "IN_PROGRESS"
    }
  ],
  "errors": [],
  "status": "IN_PROGRESS"
}
```

## Upgrade Interaction

These operations are only applicable when `PHASE_STRATEGY` is set to `STAGE`, they have no effect when it is set to `INSTALL`. See the Changing Configuration at Runtime part of the Configuring section for more information.

### Continue

```bash
$ dcos beta-kafka --name=kafka plan continue deploy
$ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/plan/deploy/continue"
```

### Interrupt

```bash
$ dcos beta-kafka --name=kafka plan pause deploy
$ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/plan/deploy/interrupt"
```

[15]: https://cwiki.apache.org/confluence/display/KAFKA/System+Tools#SystemTools-GetOffsetShell

# Appendix A - Configuration Resource

The following is an example of the configuration resource:

```json
{
  "name": "kafka",
  "role": "kafka-role",
  "principal": "kafka-principal",
  "api-port": 16962,
  "web-url": null,
  "zookeeper": "master.mesos:2181",
  "pod-specs": [
    {
      "type": "kafka",
      "user": "nobody",
      "count": 3,
      "image": null,
      "networks": [],
      "rlimits": [],
      "uris": [
        "https://downloads.mesosphere.com/kafka/assets/kafka_2.11-0.10.2.1.tgz",
        "https://infinity-artifacts.s3.amazonaws.com/autodelete7d/kafka/20170714-084852-rmq3POwXq1PqNImH/bootstrap.zip",
        "https://infinity-artifacts.s3.amazonaws.com/autodelete7d/kafka/20170714-084852-rmq3POwXq1PqNImH/executor.zip",
        "https://downloads.mesosphere.io/libmesos-bundle/libmesos-bundle-1.10-1.4-63e0814.tar.gz",
        "http://downloads.mesosphere.com/kafka/assets/kafka-statsd-metrics2-0.5.3.jar",
        "http://downloads.mesosphere.com/kafka/assets/java-dogstatsd-client-2.3.jar"
      ],
      "task-specs": [
        {
          "name": "broker",
          "goal": "RUNNING",
          "resource-set": {
            "id": "broker-resource-set",
            "resource-specifications": [
              {
                "@type": "DefaultResourceSpec",
                "name": "cpus",
                "value": {
                  "type": "SCALAR",
                  "scalar": {
                    "value": 1.0
                  },
                  "ranges": null,
                  "set": null,
                  "text": null
                },
                "role": "kafka-role",
                "pre-reserved-role": "*",
                "principal": "kafka-principal",
                "envKey": null
              },
              {
                "@type": "DefaultResourceSpec",
                "name": "mem",
                "value": {
                  "type": "SCALAR",
                  "scalar": {
                    "value": 2048.0
                  },
                  "ranges": null,
                  "set": null,
                  "text": null
                },
                "role": "kafka-role",
                "pre-reserved-role": "*",
                "principal": "kafka-principal",
                "envKey": null
              },
              {
                "@type": "NamedVIPSpec",
                "value": {
                  "type": "RANGES",
                  "scalar": null,
                  "ranges": {
                    "range": [
                      {
                        "begin": 0,
                        "end": 0
                      }
                    ]
                  },
                  "set": null,
                  "text": null
                },
                "role": "kafka-role",
                "pre-reserved-role": "*",
                "principal": "kafka-principal",
                "env-key": "KAFKA_BROKER_PORT",
                "port-name": "broker",
                "protocol": "tcp",
                "visibility": "EXTERNAL",
                "vip-name": "broker",
                "vip-port": 9092,
                "networkNames": [],
                "name": "ports"
              }
            ],
            "volume-specifications": [
              {
                "@type": "DefaultVolumeSpec",
                "type": "ROOT",
                "container-path": "kafka-broker-data",
                "name": "disk",
                "value": {
                  "type": "SCALAR",
                  "scalar": {
                    "value": 5000.0
                  },
                  "ranges": null,
                  "set": null,
                  "text": null
                },
                "role": "kafka-role",
                "pre-reserved-role": "*",
                "principal": "kafka-principal",
                "envKey": "DISK_SIZE"
              }
            ],
            "role": "kafka-role",
            "principal": "kafka-principal"
          },
          "command-spec": {
            "value": "mv -v *statsd*.jar $MESOS_SANDBOX/kafka_2.11-0.10.2.1/libs/ && ./bootstrap -resolve=false && exec $MESOS_SANDBOX/kafka_2.11-0.10.2.1/bin/kafka-server-start.sh $MESOS_SANDBOX/kafka_2.11-0.10.2.1/config/server.properties\n",
            "environment": {
              "KAFKA_ADVERTISE_HOST": "set",
              "KAFKA_AUTO_CREATE_TOPICS_ENABLE": "true",
              "KAFKA_AUTO_LEADER_REBALANCE_ENABLE": "true",
              "KAFKA_BACKGROUND_THREADS": "10",
              "KAFKA_COMPRESSION_TYPE": "producer",
              "KAFKA_CONNECTIONS_MAX_IDLE_MS": "600000",
              "KAFKA_CONTROLLED_SHUTDOWN_ENABLE": "true",
              "KAFKA_CONTROLLED_SHUTDOWN_MAX_RETRIES": "3",
              "KAFKA_CONTROLLED_SHUTDOWN_RETRY_BACKOFF_MS": "5000",
              "KAFKA_CONTROLLER_SOCKET_TIMEOUT_MS": "30000",
              "KAFKA_DEFAULT_REPLICATION_FACTOR": "1",
              "KAFKA_DELETE_TOPIC_ENABLE": "false",
              "KAFKA_DISK_PATH": "kafka-broker-data",
              "KAFKA_FETCH_PURGATORY_PURGE_INTERVAL_REQUESTS": "1000",
              "KAFKA_GROUP_MAX_SESSION_TIMEOUT_MS": "300000",
              "KAFKA_GROUP_MIN_SESSION_TIMEOUT_MS": "6000",
              "KAFKA_HEAP_OPTS": "-Xms512M -Xmx512M",
              "KAFKA_INTER_BROKER_PROTOCOL_VERSION": "0.10.0.0",
              "KAFKA_LEADER_IMBALANCE_CHECK_INTERVAL_SECONDS": "300",
              "KAFKA_LEADER_IMBALANCE_PER_BROKER_PERCENTAGE": "10",
              "KAFKA_LOG_CLEANER_BACKOFF_MS": "15000",
              "KAFKA_LOG_CLEANER_DEDUPE_BUFFER_SIZE": "134217728",
              "KAFKA_LOG_CLEANER_DELETE_RETENTION_MS": "86400000",
              "KAFKA_LOG_CLEANER_ENABLE": "true",
              "KAFKA_LOG_CLEANER_IO_BUFFER_LOAD_FACTOR": "0.9",
              "KAFKA_LOG_CLEANER_IO_BUFFER_SIZE": "524288",
              "KAFKA_LOG_CLEANER_IO_MAX_BYTES_PER_SECOND": "1.7976931348623157E308",
              "KAFKA_LOG_CLEANER_MIN_CLEANABLE_RATIO": "0.5",
              "KAFKA_LOG_CLEANER_THREADS": "1",
              "KAFKA_LOG_CLEANUP_POLICY": "delete",
              "KAFKA_LOG_FLUSH_INTERVAL_MESSAGES": "9223372036854775807",
              "KAFKA_LOG_FLUSH_OFFSET_CHECKPOINT_INTERVAL_MS": "60000",
              "KAFKA_LOG_FLUSH_SCHEDULER_INTERVAL_MS": "9223372036854775807",
              "KAFKA_LOG_INDEX_INTERVAL_BYTES": "4096",
              "KAFKA_LOG_INDEX_SIZE_MAX_BYTES": "10485760",
              "KAFKA_LOG_MESSAGE_FORMAT_VERSION": "0.10.0",
              "KAFKA_LOG_PREALLOCATE": "false",
              "KAFKA_LOG_RETENTION_BYTES": "-1",
              "KAFKA_LOG_RETENTION_CHECK_INTERVAL_MS": "300000",
              "KAFKA_LOG_RETENTION_HOURS": "168",
              "KAFKA_LOG_ROLL_HOURS": "168",
              "KAFKA_LOG_ROLL_JITTER_HOURS": "0",
              "KAFKA_LOG_SEGMENT_BYTES": "1073741824",
              "KAFKA_LOG_SEGMENT_DELETE_DELAY_MS": "60000",
              "KAFKA_MAX_CONNECTIONS_PER_IP": "2147483647",
              "KAFKA_MAX_CONNECTIONS_PER_IP_OVERRIDES": "",
              "KAFKA_MESSAGE_MAX_BYTES": "1000012",
              "KAFKA_METRICS_NUM_SAMPLES": "2",
              "KAFKA_METRICS_REPORTERS": "com.airbnb.kafka.kafka08.StatsdMetricsReporter",
              "KAFKA_METRICS_SAMPLE_WINDOW_MS": "30000",
              "KAFKA_MIN_INSYNC_REPLICAS": "1",
              "KAFKA_NUM_IO_THREADS": "8",
              "KAFKA_NUM_NETWORK_THREADS": "3",
              "KAFKA_NUM_PARTITIONS": "1",
              "KAFKA_NUM_RECOVERY_THREADS_PER_DATA_DIR": "1",
              "KAFKA_NUM_REPLICA_FETCHERS": "1",
              "KAFKA_OFFSETS_COMMIT_REQUIRED_ACKS": "-1",
              "KAFKA_OFFSETS_COMMIT_TIMEOUT_MS": "5000",
              "KAFKA_OFFSETS_LOAD_BUFFER_SIZE": "5242880",
              "KAFKA_OFFSETS_RETENTION_CHECK_INTERVAL_MS": "600000",
              "KAFKA_OFFSETS_RETENTION_MINUTES": "1440",
              "KAFKA_OFFSETS_TOPIC_COMPRESSION_CODEC": "0",
              "KAFKA_OFFSETS_TOPIC_NUM_PARTITIONS": "50",
              "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR": "3",
              "KAFKA_OFFSETS_TOPIC_SEGMENT_BYTES": "104857600",
              "KAFKA_OFFSET_METADATA_MAX_BYTES": "4096",
              "KAFKA_PRODUCER_PURGATORY_PURGE_INTERVAL_REQUESTS": "1000",
              "KAFKA_QUEUED_MAX_REQUESTS": "500",
              "KAFKA_QUOTA_CONSUMER_DEFAULT": "9223372036854775807",
              "KAFKA_QUOTA_PRODUCER_DEFAULT": "9223372036854775807",
              "KAFKA_QUOTA_WINDOW_NUM": "11",
              "KAFKA_QUOTA_WINDOW_SIZE_SECONDS": "1",
              "KAFKA_REPLICA_FETCH_BACKOFF_MS": "1000",
              "KAFKA_REPLICA_FETCH_MAX_BYTES": "1048576",
              "KAFKA_REPLICA_FETCH_MIN_BYTES": "1",
              "KAFKA_REPLICA_FETCH_WAIT_MAX_MS": "500",
              "KAFKA_REPLICA_HIGH_WATERMARK_CHECKPOINT_INTERVAL_MS": "5000",
              "KAFKA_REPLICA_LAG_TIME_MAX_MS": "10000",
              "KAFKA_REPLICA_SOCKET_RECEIVE_BUFFER_BYTES": "65536",
              "KAFKA_REPLICA_SOCKET_TIMEOUT_MS": "30000",
              "KAFKA_REQUEST_TIMEOUT_MS": "30000",
              "KAFKA_RESERVED_BROKER_MAX_ID": "1000",
              "KAFKA_SOCKET_RECEIVE_BUFFER_BYTES": "102400",
              "KAFKA_SOCKET_REQUEST_MAX_BYTES": "104857600",
              "KAFKA_SOCKET_SEND_BUFFER_BYTES": "102400",
              "KAFKA_UNCLEAN_LEADER_ELECTION_ENABLE": "true",
              "KAFKA_ZOOKEEPER_SESSION_TIMEOUT_MS": "6000",
              "KAFKA_ZOOKEEPER_SYNC_TIME_MS": "2000",
              "KAFKA_ZOOKEEPER_URI": "master.mesos:2181/dcos-service-kafka",
              "METRIC_REPORTERS": "com.airbnb.kafka.kafka09.StatsdMetricsReporter"
            }
          },
          "health-check-spec": null,
          "readiness-check-spec": {
            "command": "# since the server is just started, the starting and started log lines\n# should be present in the server.log file, independent of whether or\n# not the server.log file is being logrotated.\n# \n# Example log lines follow:\n## starting:\n# [2017-06-14 22:20:54,260] INFO starting (kafka.server.KafkaServer)\n## started:\n# [2017-06-14 22:20:55,464] INFO [Kafka Server 1], started (kafka.server.KafkaServer)\n\nkafka_dir=$(ls -d kafka_* |head -n 1)\nkafka_server_log_file=${kafka_dir}/logs/server.log\nstarting_date_s=$(awk '/.*starting.*\\(kafka.server.KafkaServer\\)/{gsub(\"[[]\", \"\", $1);gsub(\"[]]\", \"\", $2); print $1\"T\"$2}' $kafka_server_log_file |tail -n 1)\nstarting_date=$(date -d $starting_date_s +%s)\nstarted_date_s=$(awk '/.*started.*\\(kafka.server.KafkaServer\\)/{gsub(\"[[]\", \"\", $1);gsub(\"[]]\", \"\", $2); print $1\"T\"$2}' $kafka_server_log_file |tail -n 1)\nstarted_date=$(date -d $started_date_s +%s)\n[ ! -z $started_date_s ] && \\\n    [ ! -z $starting_date_s ] && \\\n    [ $starting_date -le $started_date ]\nis_ready=$?\nexit $is_ready\n",
            "delay": 0,
            "interval": 5,
            "timeout": 10
          },
          "config-files": [
            {
              "name": "server-properties",
              "relative-path": "kafka_2.11-0.10.2.1/config/server.properties",
              "template-content": "... content of server.properties template ..."
            }
          ],
          "discovery-spec": null,
          "task-kill-grace-period-seconds": 30
        }
      ],
      "placement-rule": {
        "@type": "MaxPerHostnameRule",
        "max": 1,
        "task-filter": {
          "@type": "RegexMatcher",
          "pattern": "kafka-.*"
        }
      },
      "volumes": [],
      "pre-reserved-role": "*",
      "secrets": []
    }
  ],
  "replacement-failure-policy": null
}
```
