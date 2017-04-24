---
post_title: API Reference
menu_order: 60
feature_maturity: preview
enterprise: 'no'
---


<a name="#rest-auth"></a>
# REST API Authentication
REST API requests must be authenticated. This authentication is only applicable for interacting with the Kafka REST API directly. You do not need the token to access the Kafka nodes themselves.

If you are using Enterprise DC/OS, follow these instructions to [create a service account and an authentication token](https://docs.mesosphere.com/1.9/security/service-auth/custom-service-auth/). You can then configure your service to automatically refresh the authentication token when it expires. To get started more quickly, you can also [get the authentication token without a service account](https://docs.mesosphere.com/1.9/security/iam-api/), but you will need to manually refresh the token.

If you are using open source DC/OS, follow these instructions to [pass your authentication token to the DC/OS endpoint](https://dcos.io/docs/1.9/security/iam-api/).

Once you have the authentication token, you can store it in an environment variable and reference it in your REST API calls:

```
$ export auth_token=uSeR_t0k3n
```

The `curl` examples in this document assume that an auth token has been stored in an environment variable named `auth_token`.

If you are using Enterprise DC/OS, the security mode of your installation may also require the `--ca-cert` flag when making REST calls. Refer to [Obtaining and passing the DC/OS certificate in cURL requests](https://docs.mesosphere.com/1.9/networking/tls-ssl/#get-dcos-cert) for information on how to use the `--cacert` flag. [If your security mode is `disabled`](https://docs.mesosphere.com/1.9/networking/tls-ssl/), do not use the `--ca-cert` flag.

For ongoing maintenance of the Kafka cluster itself, the Kafka service exposes an HTTP API whose structure is designed to roughly match the tools provided by the Kafka distribution, such as `bin/kafka-topics.sh`.

The examples here provide equivalent commands using both the [DC/OS CLI](https://github.com/mesosphere/dcos-cli) (with the `kafka` CLI module installed) and `curl`. These examples assume a service named `kafka` (the default), and the `curl` examples assume a DC/OS cluster path of `<dcos_url>`. Replace these with appropriate values as needed.

The `dcos kafka` CLI commands have a `--name` argument, allowing the user to specify which Kafka instance to query. The value defaults to `kafka`, so it's technically redundant to specify `--name=kafka` in these examples.

# Connection Information

Kafka comes with many useful tools of its own that often require either Zookeeper connection information or the list of broker endpoints. This information can be retrieved in an easily consumable format from the `/connection` endpoint:

    $ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/connection"
    GET /service/kafka/v1/connection HTTP/1.1

    {
        "address": [
            "10.0.0.211:9843",
            "10.0.0.217:10056",
            "10.0.0.214:9689"
        ],
        "dns": [
            "broker-0.kafka.mesos:9843",
            "broker-1.kafka.mesos:10056",
            "broker-2.kafka.mesos:9689"
        ],
        "vip": "broker.kafka.l4lb.thisdcos.directory:9092",
        "zookeeper": "master.mesos:2181/dcos-service-kafka"
    }

The same information can be retrieved through the DC/OS CLI:

    $ dcos kafka connection
    {
        "address": [
            "10.0.0.211:9843",
            "10.0.0.217:10056",
            "10.0.0.214:9689"
        ],
        "dns": [
            "broker-0.kafka.mesos:9843",
            "broker-1.kafka.mesos:10056",
            "broker-2.kafka.mesos:9689"
        ],
        "vip": "broker.kafka.l4lb.thisdcos.directory:9092",
        "zookeeper": "master.mesos:2181/dcos-service-kafka"
    }


# Broker Operations

## Add Broker

Increase the `BROKER_COUNT` value via Marathon. This should be rolled as in any other configuration update.

## List All Brokers

    $ dcos kafka --name=kafka broker list
    {
        "brokers": [
            "0",
            "1",
            "2"
        ]
    }


    $ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/brokers"
    GET /service/kafka/v1/brokers HTTP/1.1

    {
        "brokers": [
            "0",
            "1",
            "2"
        ]
    }


## Restart Single Broker

Restarts the broker in-place.

    $ dcos kafka --name=kafka broker restart 0
    [
        "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
    ]


    $ curl -X PUT -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/brokers/0"
    PUT /service/kafka/v1/brokers/0 HTTP/1.1

    [
        "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
    ]


## Replace Single Broker

Restarts the broker and replaces its existing resource/volume allocations. The new broker instance may also be placed on a different machine.

    $ dcos kafka --name=kafka broker replace 0
    [
        "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
    ]


    $ curl -X PUT -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/brokers/0?replace=true"
    PUT /service/kafka/v1/brokers/0 HTTP/1.1

    [
        "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
    ]


# Topic Operations

These operations mirror what is available with `bin/kafka-topics.sh`.

## List Topics

    $ dcos kafka --name=kafka topic list
    [
        "topic1",
        "topic0"
    ]


    $ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics"
    GET /service/kafka/v1/topics HTTP/1.1

    [
        "topic1",
        "topic0"
    ]


## Describe Topic

    $ dcos kafka --name=kafka topic describe topic1
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


    $ curl -X POST -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/topic1"
    GET /service/kafka/v1/topics/topic1 HTTP/1.1

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


## Create Topic

    $ dcos kafka --name=kafka topic create topic1 --partitions=3 --replication=3
    {
        "message": "Output: Created topic "topic1".n"
    }


    $ curl -X POST -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics?name=topic1&partitions=3&replication=3"
    POST /service/kafka/v1/topics?replication=3&name=topic1&partitions=3 HTTP/1.1

    {
        "message": "Output: Created topic "topic1".n"
    }


## View Topic Offsets

There is an optional `--time` parameter which may be set to either "first", "last", or a timestamp in milliseconds as [described in the Kafka documentation][15].

    $ dcos kafka --name=kafka topic offsets topic1 --time=last
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


    $ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/topic1/offsets?time=last"
    GET /service/kafka/v1/topics/topic1/offsets?time=last HTTP/1.1

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


## Alter Topic Partition Count

    $ dcos kafka --name=kafka topic partitions topic1 2
    {
        "message": "Output: WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affectednAdding partitions succeeded!n"
    }


    $ curl -X PUT -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/topic1?operation=partitions&partitions=2"
    PUT /service/kafka/v1/topics/topic1?operation=partitions&partitions=2 HTTP/1.1

    {
        "message": "Output: WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affectednAdding partitions succeeded!n"
    }


## Run Producer Test on Topic

    $ dcos kafka --name=kafka topic producer_test topic1 10

    {
        "message": "10 records sent, 70.422535 records/sec (0.07 MB/sec), 24.20 ms avg latency, 133.00 ms max latency, 13 ms 50th, 133 ms 95th, 133 ms 99th, 133 ms 99.9th.n"
    }


    $ curl -X PUT -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/topic1?operation=producer-test&messages=10"
    PUT /service/kafka/v1/topics/topic1?operation=producer-test&messages=10 HTTP/1.1

    {
        "message": "10 records sent, 70.422535 records/sec (0.07 MB/sec), 24.20 ms avg latency, 133.00 ms max latency, 13 ms 50th, 133 ms 95th, 133 ms 99th, 133 ms 99.9th.n"
    }


Runs the equivalent of the following command from the machine running the Kafka Scheduler:

    kafka-producer-perf-test.sh
        --topic <topic>
        --num-records <messages>
        --throughput 100000
        --record-size 1024
        --producer-props bootstrap.servers=<current broker endpoints>


## Delete Topic

    $ dcos kafka --name=kafka topic delete topic1

    {
        "message": "Topic topic1 is marked for deletion.nNote: This will have no impact if delete.topic.enable is not set to true.n"
    }


    $ curl -X DELETE -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/topic1"
    DELETE /service/kafka/v1/topics/topic1 HTTP/1.1

    {
        "message": "Topic topic1 is marked for deletion.nNote: This will have no impact if delete.topic.enable is not set to true.n"
    }


Note the warning in the output from the commands above. You can change the indicated "delete.topic.enable" configuration value as a configuration change.

## List Under Replicated Partitions

    $ dcos kafka --name=kafka topic under_replicated_partitions

    {
        "message": ""
    }


    $ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/under_replicated_partitions"
    GET /service/kafka/v1/topics/under_replicated_partitions HTTP/1.1

    {
        "message": ""
    }


## List Unavailable Partitions

    $ dcos kafka --name=kafka topic unavailable_partitions

    {
        "message": ""
    }


    $ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/topics/unavailable_partitions"
    GET /service/kafka/v1/topics/unavailable_partitions HTTP/1.1

    {
        "message": ""
    }

# Service Status

Send a GET request to the `/v1/state/properties/suppressed` endpoint to learn if Kafka is in a `suppressed` state and not receiving offers. If a service does not need offers, Mesos can "suppress" it so that other services are not starved for resources.

You can use this request to troubleshoot: if you think Kafka should be receiving resource offers, but is not, you can use this API call to see if Kafka is suppressed. You will receive a response of `true` or `false`.

```
curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/state/properties/suppressed"
```

# Config History

These operations relate to viewing the service's configuration history.

## List Configuration IDs

    $ dcos kafka --name=kafka config list

    [
        "319ebe89-42e2-40e2-9169-8568e2421023",
        "294235f2-8504-4194-b43d-664443f2132b"
    ]


    $ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/configurations"
    GET /service/kafka/v1/configurations HTTP/1.1

    [
        "319ebe89-42e2-40e2-9169-8568e2421023",
        "294235f2-8504-4194-b43d-664443f2132b"
    ]


## Describe Configuration

This configuration shows a default per-broker memory allocation of 2048 (configured via the `BROKER_MEM` parameter):

    $ dcos kafka --name=kafka config describe 319ebe89-42e2-40e2-9169-8568e2421023

    {
        "brokerConfiguration": {
            "containerHookUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/container-hook-0.2.5.tgz",
            "cpus": 1,
            "disk": 5000,
            "diskType": "ROOT",
            "javaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/jre-8u72-linux-x64.tar.gz",
            "kafkaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/kafka_2.10-0.9.0.1.tgz",
            "mem": 2048, // <<--
            "overriderUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/overrider.zip"
        },
        "kafkaConfiguration": {
            [...]
        },
        "serviceConfiguration": {
            "count": 3,
            "name": "kafka",
            "phaseStrategy": "INSTALL",
            "placementStrategy": "NODE",
            "principal": "kafka-principal",
            "role": "kafka-role",
            "user": ""
        }
    }


    $ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/configurations/319ebe89-42e2-40e2-9169-8568e2421023"
    GET /service/kafka/v1/configurations/319ebe89-42e2-40e2-9169-8568e2421023 HTTP/1.1

    {
        "brokerConfiguration": {
            "containerHookUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/container-hook-0.2.5.tgz",
            "cpus": 1,
            "disk": 5000,
            "diskType": "ROOT",
            "javaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/jre-8u72-linux-x64.tar.gz",
            "kafkaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/kafka_2.10-0.9.0.1.tgz",
            "mem": 2048, // <<--
            "overriderUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/overrider.zip"
        },
        "kafkaConfiguration": {
            [...]
        },
        "serviceConfiguration": {
            "count": 3,
            "name": "kafka",
            "phaseStrategy": "INSTALL",
            "placementStrategy": "NODE",
            "principal": "kafka-principal",
            "role": "kafka-role",
            "user": ""
        }
    }


## Describe Target Configuration

The target configuration, meanwhile, shows an increase of configured per-broker memory from 2048 to 4096 (again, configured as `BROKER_MEM`):

    $ dcos kafka --name=kafka config target

    {
        "brokerConfiguration": {
            "containerHookUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/container-hook-0.2.5.tgz",
            "cpus": 1,
            "disk": 5000,
            "diskType": "ROOT",
            "javaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/jre-8u72-linux-x64.tar.gz",
            "kafkaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/kafka_2.10-0.9.0.1.tgz",
            "mem": 4096, // <<--
            "overriderUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/overrider.zip"
        },
        "kafkaConfiguration": {
            [...]
        },
        "serviceConfiguration": {
            "count": 3,
            "name": "kafka",
            "phaseStrategy": "INSTALL",
            "placementStrategy": "NODE",
            "principal": "kafka-principal",
            "role": "kafka-role",
            "user": ""
        }
    }


    $ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/configurations/target"
    GET /service/kafka/v1/configurations/target HTTP/1.1

    {
        "brokerConfiguration": {
            "containerHookUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/container-hook-0.2.5.tgz",
            "cpus": 1,
            "disk": 5000,
            "diskType": "ROOT",
            "javaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/jre-8u72-linux-x64.tar.gz",
            "kafkaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/kafka_2.10-0.9.0.1.tgz",
            "mem": 4096, // <<--
            "overriderUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/overrider.zip"
        },
        "kafkaConfiguration": {
            [...]
        },
        "serviceConfiguration": {
            "count": 3,
            "name": "kafka",
            "phaseStrategy": "INSTALL",
            "placementStrategy": "NODE",
            "principal": "kafka-principal",
            "role": "kafka-role",
            "user": ""
        }
    }


# Config Updates

These options relate to viewing and controlling rollouts and configuration updates.

## View Plan Status

Displays all Phases and Steps in the service Plan. If a rollout is currently in progress, this returns a 503 HTTP code with response content otherwise unchanged.

    $ dcos kafka --name=kafka plan
    GET /service/kafka/v1/plan HTTP/1.1

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


    $ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/plan"
    GET /service/kafka/v1/plan HTTP/1.1

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
              "status": "COMPLETE",
              "name": "broker-1",
              "message": "Broker-1 is COMPLETE"
            },
            {
              "id": "a2ba3969-cb18-4a05-abd0-4186afe0f840",
              "status": "COMPLETE",
              "name": "broker-2",
              "message": "Broker-2 is COMPLETE"
            }
          ],
          "status": "COMPLETE"
        }
      ],
      "errors": [],
      "status": "COMPLETE"
    }


## Upgrade Interaction

These operations are only applicable when `PHASE_STRATEGY` is set to `STAGE`, they have no effect when it is set to `INSTALL`. See the Changing Configuration at Runtime part of the Configuring section for more information.

### Continue

    $ dcos kafka --name=kafka continue
    $ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/plan/continue"


### Interrupt

    $ dcos kafka --name=kafka interrupt
    $ curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/plan/interrupt"


 [15]: https://cwiki.apache.org/confluence/display/KAFKA/System+Tools#SystemTools-GetOffsetShell
