---
post_title: API Reference
menu_order: 70
enterprise: 'no'
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
              "template-content": "# Licensed to the Apache Software Foundation (ASF) under one or more\n# contributor license agreements.  See the NOTICE file distributed with\n# this work for additional information regarding copyright ownership.\n# The ASF licenses this file to You under the Apache License, Version 2.0\n# (the \"License\"); you may not use this file except in compliance with\n# the License.  You may obtain a copy of the License at\n#\n#    http://www.apache.org/licenses/LICENSE-2.0\n#\n# Unless required by applicable law or agreed to in writing, software\n# distributed under the License is distributed on an \"AS IS\" BASIS,\n# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n# See the License for the specific language governing permissions and\n# limitations under the License.\n# see kafka.server.KafkaConfig for additional details and defaults\n\n############################# Server Basics #############################\n\n# The id of the broker. This must be set to a unique integer for each broker.\nbroker.id={{POD_INSTANCE_INDEX}}\n\n############################# Socket Server Settings #############################\n\n# The address the socket server listens on. It will get the value returned from \n# java.net.InetAddress.getCanonicalHostName() if not configured.\n#   FORMAT:\n#     listeners = security_protocol://host_name:port\n#   EXAMPLE:\n#     listeners = PLAINTEXT://your.host.name:9092\nlisteners=PLAINTEXT://:{{KAFKA_BROKER_PORT}}\n\n# Hostname and port the broker will advertise to producers and consumers. If not set, \n# it uses the value for \"listeners\" if configured.  Otherwise, it will use the value\n# returned from java.net.InetAddress.getCanonicalHostName().\n#advertised.listeners=PLAINTEXT://your.host.name:9092\n\n# The number of threads handling network requests\nnum.network.threads={{KAFKA_NUM_NETWORK_THREADS}}\n\n# The number of threads doing disk I/O\nnum.io.threads={{KAFKA_NUM_IO_THREADS}}\n\n# The send buffer (SO_SNDBUF) used by the socket server\nsocket.send.buffer.bytes={{KAFKA_SOCKET_SEND_BUFFER_BYTES}}\n\n# The receive buffer (SO_RCVBUF) used by the socket server\nsocket.receive.buffer.bytes={{KAFKA_SOCKET_RECEIVE_BUFFER_BYTES}}\n\n# The maximum size of a request that the socket server will accept (protection against OOM)\nsocket.request.max.bytes={{KAFKA_SOCKET_REQUEST_MAX_BYTES}}\n\n\n############################# Log Basics #############################\n\n# A comma seperated list of directories under which to store log files\nlog.dirs={{KAFKA_DISK_PATH}}/broker-{{POD_INSTANCE_INDEX}}\n\n# The default number of log partitions per topic. More partitions allow greater\n# parallelism for consumption, but this will also result in more files across\n# the brokers.\nnum.partitions={{KAFKA_NUM_PARTITIONS}}\n\n# The number of threads per data directory to be used for log recovery at startup and flushing at shutdown.\n# This value is recommended to be increased for installations with data dirs located in RAID array.\nnum.recovery.threads.per.data.dir={{KAFKA_NUM_RECOVERY_THREADS_PER_DATA_DIR}}\n\n############################# Log Flush Policy #############################\n\n# Messages are immediately written to the filesystem but by default we only fsync() to sync\n# the OS cache lazily. The following configurations control the flush of data to disk.\n# There are a few important trade-offs here:\n#    1. Durability: Unflushed data may be lost if you are not using replication.\n#    2. Latency: Very large flush intervals may lead to latency spikes when the flush does occur as there will be a lot of data to flush.\n#    3. Throughput: The flush is generally the most expensive operation, and a small flush interval may lead to exceessive seeks.\n# The settings below allow one to configure the flush policy to flush data after a period of time or\n# every N messages (or both). This can be done globally and overridden on a per-topic basis.\n\n# The number of messages to accept before forcing a flush of data to disk\nlog.flush.interval.messages={{KAFKA_LOG_FLUSH_INTERVAL_MESSAGES}}\n\n# The maximum amount of time a message can sit in a log before we force a flush\n#log.flush.interval.ms=1000\n\n############################# Log Retention Policy #############################\n\n# The following configurations control the disposal of log segments. The policy can\n# be set to delete segments after a period of time, or after a given size has accumulated.\n# A segment will be deleted whenever *either* of these criteria are met. Deletion always happens\n# from the end of the log.\n\n# The minimum age of a log file to be eligible for deletion\nlog.retention.hours={{KAFKA_LOG_RETENTION_HOURS}}\n\n# A size-based retention policy for logs. Segments are pruned from the log as long as the remaining\n# segments don't drop below log.retention.bytes.\nlog.retention.bytes={{KAFKA_LOG_RETENTION_BYTES}}\n\n# The maximum size of a log segment file. When this size is reached a new log segment will be created.\nlog.segment.bytes={{KAFKA_LOG_SEGMENT_BYTES}}\n\n# The interval at which log segments are checked to see if they can be deleted according\n# to the retention policies\nlog.retention.check.interval.ms={{KAFKA_LOG_RETENTION_CHECK_INTERVAL_MS}}\n\n############################# Zookeeper #############################\n\n# Zookeeper connection string (see zookeeper docs for details).\n# This is a comma separated host:port pairs, each corresponding to a zk\n# server. e.g. \"127.0.0.1:3000,127.0.0.1:3001,127.0.0.1:3002\".\n# You can also append an optional chroot string to the urls to specify the\n# root directory for all kafka znodes.\nzookeeper.connect={{KAFKA_ZOOKEEPER_URI}}\n\n# Timeout in ms for connecting to zookeeper\nzookeeper.connection.timeout.ms=6000\n\n\n########################### Addition Parameters ########################\n\nexternal.kafka.statsd.port={{STATSD_UDP_PORT}}\nexternal.kafka.statsd.host={{STATSD_UDP_HOST}}\nexternal.kafka.statsd.reporter.enabled=true\nexternal.kafka.statsd.tag.enabled=true\nexternal.kafka.statsd.metrics.exclude_regex=\n\n{{#KAFKA_ADVERTISE_HOST}}\n# TODO this is wrong when overlay network is enabled\nadvertised.host.name={{LIBPROCESS_IP}}\n{{/KAFKA_ADVERTISE_HOST}}\n\nport={{KAFKA_BROKER_PORT}}\n\nauto.create.topics.enable={{KAFKA_AUTO_CREATE_TOPICS_ENABLE}}\nauto.leader.rebalance.enable={{KAFKA_AUTO_LEADER_REBALANCE_ENABLE}}\n\nbackground.threads={{KAFKA_BACKGROUND_THREADS}}\n\ncompression.type={{KAFKA_COMPRESSION_TYPE}}\n\nconnections.max.idle.ms={{KAFKA_CONNECTIONS_MAX_IDLE_MS}}\n\ncontrolled.shutdown.enable={{KAFKA_CONTROLLED_SHUTDOWN_ENABLE}}\ncontrolled.shutdown.max.retries={{KAFKA_CONTROLLED_SHUTDOWN_MAX_RETRIES}}\ncontrolled.shutdown.retry.backoff.ms={{KAFKA_CONTROLLED_SHUTDOWN_RETRY_BACKOFF_MS}}\ncontroller.socket.timeout.ms={{KAFKA_CONTROLLER_SOCKET_TIMEOUT_MS}}\n\ndefault.replication.factor={{KAFKA_DEFAULT_REPLICATION_FACTOR}}\n\ndelete.topic.enable={{KAFKA_DELETE_TOPIC_ENABLE}}\n\nfetch.purgatory.purge.interval.requests={{KAFKA_FETCH_PURGATORY_PURGE_INTERVAL_REQUESTS}}\n\ngroup.max.session.timeout.ms={{KAFKA_GROUP_MAX_SESSION_TIMEOUT_MS}}\ngroup.min.session.timeout.ms={{KAFKA_GROUP_MIN_SESSION_TIMEOUT_MS}}\n\ninter.broker.protocol.version={{KAFKA_INTER_BROKER_PROTOCOL_VERSION}}\n\nleader.imbalance.check.interval.seconds={{KAFKA_LEADER_IMBALANCE_CHECK_INTERVAL_SECONDS}}\nleader.imbalance.per.broker.percentage={{KAFKA_LEADER_IMBALANCE_PER_BROKER_PERCENTAGE}}\n\nlog.cleaner.backoff.ms={{KAFKA_LOG_CLEANER_BACKOFF_MS}}\nlog.cleaner.dedupe.buffer.size={{KAFKA_LOG_CLEANER_DEDUPE_BUFFER_SIZE}}\nlog.cleaner.delete.retention.ms={{KAFKA_LOG_CLEANER_DELETE_RETENTION_MS}}\nlog.cleaner.enable={{KAFKA_LOG_CLEANER_ENABLE}}\nlog.cleaner.io.buffer.load.factor={{KAFKA_LOG_CLEANER_IO_BUFFER_LOAD_FACTOR}}\nlog.cleaner.io.buffer.size={{KAFKA_LOG_CLEANER_IO_BUFFER_SIZE}}\nlog.cleaner.io.max.bytes.per.second={{KAFKA_LOG_CLEANER_IO_MAX_BYTES_PER_SECOND}}\nlog.cleaner.min.cleanable.ratio={{KAFKA_LOG_CLEANER_MIN_CLEANABLE_RATIO}}\nlog.cleaner.threads={{KAFKA_LOG_CLEANER_THREADS}}\nlog.cleanup.policy={{KAFKA_LOG_CLEANUP_POLICY}}\n\nlog.flush.offset.checkpoint.interval.ms={{KAFKA_LOG_FLUSH_OFFSET_CHECKPOINT_INTERVAL_MS}}\nlog.flush.scheduler.interval.ms={{KAFKA_LOG_FLUSH_SCHEDULER_INTERVAL_MS}}\n\nlog.index.interval.bytes={{KAFKA_LOG_INDEX_INTERVAL_BYTES}}\nlog.index.size.max.bytes={{KAFKA_LOG_INDEX_SIZE_MAX_BYTES}}\n\nlog.message.format.version={{KAFKA_LOG_MESSAGE_FORMAT_VERSION}}\n\nlog.preallocate={{KAFKA_LOG_PREALLOCATE}}\n\nlog.roll.hours={{KAFKA_LOG_ROLL_HOURS}}\nlog.roll.jitter.hours={{KAFKA_LOG_ROLL_JITTER_HOURS}}\n\nlog.segment.delete.delay.ms={{KAFKA_LOG_SEGMENT_DELETE_DELAY_MS}}\n\nmax.connections.per.ip.overrides={{KAFKA_MAX_CONNECTIONS_PER_IP_OVERRIDES}}\nmax.connections.per.ip={{KAFKA_MAX_CONNECTIONS_PER_IP}}\n\nmessage.max.bytes={{KAFKA_MESSAGE_MAX_BYTES}}\n\nkafka.metrics.reporters={{KAFKA_METRICS_REPORTERS}}\nmetric.reporters={{METRIC_REPORTERS}}\nmetrics.num.samples={{KAFKA_METRICS_NUM_SAMPLES}}\nmetrics.sample.window.ms={{KAFKA_METRICS_SAMPLE_WINDOW_MS}}\n\nmin.insync.replicas={{KAFKA_MIN_INSYNC_REPLICAS}}\n\nnum.replica.fetchers={{KAFKA_NUM_REPLICA_FETCHERS}}\n\noffset.metadata.max.bytes={{KAFKA_OFFSET_METADATA_MAX_BYTES}}\noffsets.commit.required.acks={{KAFKA_OFFSETS_COMMIT_REQUIRED_ACKS}}\noffsets.commit.timeout.ms={{KAFKA_OFFSETS_COMMIT_TIMEOUT_MS}}\noffsets.load.buffer.size={{KAFKA_OFFSETS_LOAD_BUFFER_SIZE}}\noffsets.retention.check.interval.ms={{KAFKA_OFFSETS_RETENTION_CHECK_INTERVAL_MS}}\noffsets.retention.minutes={{KAFKA_OFFSETS_RETENTION_MINUTES}}\noffsets.topic.compression.codec={{KAFKA_OFFSETS_TOPIC_COMPRESSION_CODEC}}\noffsets.topic.num.partitions={{KAFKA_OFFSETS_TOPIC_NUM_PARTITIONS}}\noffsets.topic.replication.factor={{KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR}}\noffsets.topic.segment.bytes={{KAFKA_OFFSETS_TOPIC_SEGMENT_BYTES}}\n\nproducer.purgatory.purge.interval.requests={{KAFKA_PRODUCER_PURGATORY_PURGE_INTERVAL_REQUESTS}}\n\nqueued.max.requests={{KAFKA_QUEUED_MAX_REQUESTS}}\nquota.consumer.default={{KAFKA_QUOTA_CONSUMER_DEFAULT}}\nquota.producer.default={{KAFKA_QUOTA_PRODUCER_DEFAULT}}\nquota.window.num={{KAFKA_QUOTA_WINDOW_NUM}}\nquota.window.size.seconds={{KAFKA_QUOTA_WINDOW_SIZE_SECONDS}}\n\nreplica.fetch.backoff.ms={{KAFKA_REPLICA_FETCH_BACKOFF_MS}}\nreplica.fetch.max.bytes={{KAFKA_REPLICA_FETCH_MAX_BYTES}}\nreplica.fetch.min.bytes={{KAFKA_REPLICA_FETCH_MIN_BYTES}}\nreplica.fetch.wait.max.ms={{KAFKA_REPLICA_FETCH_WAIT_MAX_MS}}\nreplica.high.watermark.checkpoint.interval.ms={{KAFKA_REPLICA_HIGH_WATERMARK_CHECKPOINT_INTERVAL_MS}}\nreplica.lag.time.max.ms={{KAFKA_REPLICA_LAG_TIME_MAX_MS}}\nreplica.socket.receive.buffer.bytes={{KAFKA_REPLICA_SOCKET_RECEIVE_BUFFER_BYTES}}\nreplica.socket.timeout.ms={{KAFKA_REPLICA_SOCKET_TIMEOUT_MS}}\n\nrequest.timeout.ms={{KAFKA_REQUEST_TIMEOUT_MS}}\n\nreserved.broker.max.id={{KAFKA_RESERVED_BROKER_MAX_ID}}\n\nunclean.leader.election.enable={{KAFKA_UNCLEAN_LEADER_ELECTION_ENABLE}}\n\nzookeeper.session.timeout.ms={{KAFKA_ZOOKEEPER_SESSION_TIMEOUT_MS}}\nzookeeper.sync.time.ms={{KAFKA_ZOOKEEPER_SYNC_TIME_MS}}\n\n########################################################################\n"
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
