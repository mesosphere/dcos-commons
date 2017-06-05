---
post_title: Configure
menu_order: 30
feature_maturity: preview
enterprise: 'no'
---












<a name="changing-configuration-at-runtime"></a>

# Changing Configuration at Runtime

You can customize your cluster in-place when it is up and running.

The Kafka scheduler runs as a Marathon process and can be reconfigured by changing values from the DC/OS web interface. These are the general steps to follow:

1.  Go to the `Services` tab of the DC/OS web interface.
1.  Click the name of the Kafka service to be updated.
1.  Within the Kafka instance details view, click the menu in the upper right, then choose **Edit**.
1.  In the dialog that appears, click the **Environment** tab and update any field(s) to their desired value(s). For example, to [increase the number of Brokers][8], edit the value for `BROKER_COUNT`. Do not edit the value for `FRAMEWORK_NAME` or `BROKER_DISK`.
1.  Choose a `DEPLOY_STRATEGY`: serial, serial-canary, parallel-canary, or parallel. See the SDK Developer guide for more information on [deployment plan strategies](https://mesosphere.github.io/dcos-commons/developer-guide.html#plans). <!-- I'm not sure I like this solution, since users aren't going to have the context for the dev guide). -->
1.  Click **REVIEW & RUN** to apply any changes and cleanly reload the Kafka scheduler. The Kafka cluster itself will persist across the change.

## Configuration Update REST API

Make the REST request below to view the current deployment plan. See the REST API Authentication part of the REST API Reference section for information on how this request must be authenticated.

    curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/plans/deploy"

    {
      "phases" : [ {
        "id" : "52f052a5-9732-427f-970d-eac972c0aa09",
        "name" : "Deployment",
        "steps" : [ {
          "id" : "0cb35760-d13f-4c21-8d7f-286b8f14834b",
          "status" : "COMPLETE",
          "name" : "kafka-0:[broker]",
          "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-0:[broker] [0cb35760-d13f-4c21-8d7f-286b8f14834b]' has status: 'COMPLETE'."
        }, {
          "id" : "b5a12959-02a4-4039-9566-ca0077c398fc",
          "status" : "COMPLETE",
          "name" : "kafka-1:[broker]",
          "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-1:[broker] [b5a12959-02a4-4039-9566-ca0077c398fc]' has status: 'COMPLETE'."
        }, {
          "id" : "5f252649-28c1-4555-82dd-3ebf2971b9b7",
          "status" : "COMPLETE",
          "name" : "kafka-2:[broker]",
          "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-2:[broker] [5f252649-28c1-4555-82dd-3ebf2971b9b7]' has status: 'COMPLETE'."
        } ],
        "status" : "COMPLETE"
      } ],
      "errors" : [ ],
      "status" : "COMPLETE"
    }

<!-- need to update this with current information for different deployments
When using the `serial-canary` or `parallel-canary` deployment strategy, an update plan will initially pause without doing any update to ensure the plan is correct. It will look like this:

    curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/plans/deploy"
    GET <dcos_url>/service/kafka/v1/plans/deploy HTTP/1.1

{
  "phases" : [ {
    "id" : "85d43c31-f29a-43d9-b1f1-3c7ec4afa780",
    "name" : "Deployment",
    "steps" : [ {
      "id" : "ac3a0842-1a1f-4181-9472-830f418ef430",
      "status" : "WAITING",
      "name" : "kafka-0:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-0:[broker] [ac3a0842-1a1f-4181-9472-830f418ef430]' has status: 'WAITING'."
    }, {
      "id" : "01f83325-4024-4b71-b5a5-7c316f1f3c41",
      "status" : "WAITING",
      "name" : "kafka-1:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-1:[broker] [01f83325-4024-4b71-b5a5-7c316f1f3c41]' has status: 'WAITING'."
    }, {
      "id" : "6a17eb0d-fe8b-4244-94df-f7b90fab5142",
      "status" : "PENDING",
      "name" : "kafka-2:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-2:[broker] [6a17eb0d-fe8b-4244-94df-f7b90fab5142]' has status: 'PENDING'."
    } ],
    "status" : "WAITING"
  } ],
  "errors" : [ ],
  "status" : "WAITING"
}


Enter the `continue` command to execute the first step:

    curl -X POST -H "Authorization: token=$auth_token" 
    "<dcos_url>/service/kafka/v1/plans/deploy/continue?phase=Deployment"
    POST <dcos_url>/service/kafka/v1/plans/deploy/continue?phase=Deployment HTTP/1.1

    {
        "Result": "Received cmd: continue"
    }


After you execute the continue operation, the plan will look like this:

    curl -H "Authorization: token=$auth_token" 
    "<dcos_url>/service/kafka/v1/plans/deploy"
    GET <dcos_url>/service/kafka/v1/plans/deploy HTTP/1.1

{
  "phases" : [ {
    "id" : "85d43c31-f29a-43d9-b1f1-3c7ec4afa780",
    "name" : "Deployment",
    "steps" : [ {
      "id" : "ac3a0842-1a1f-4181-9472-830f418ef430",
      "status" : "COMPLETE",
      "name" : "kafka-0:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-0:[broker] [ac3a0842-1a1f-4181-9472-830f418ef430]' has status: 'COMPLETE'."
    }, {
      "id" : "01f83325-4024-4b71-b5a5-7c316f1f3c41",
      "status" : "WAITING",
      "name" : "kafka-1:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-1:[broker] [01f83325-4024-4b71-b5a5-7c316f1f3c41]' has status: 'WAITING'."
    }, {
      "id" : "6a17eb0d-fe8b-4244-94df-f7b90fab5142",
      "status" : "PENDING",
      "name" : "kafka-2:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-2:[broker] [6a17eb0d-fe8b-4244-94df-f7b90fab5142]' has status: 'PENDING'."
    } ],
    "status" : "WAITING"
  } ],
  "errors" : [ ],
  "status" : "WAITING"
}  



If you enter `continue` a second time, the rest of the plan will be executed without further interruption. If you want to interrupt a configuration update that is in progress, enter the `interrupt` command:

    curl -X POST -H "Authorization: token=$auth_token"  
    
    "<dcos_url>/service/kafka/v1/plans/deploy/interrupt?phase=Deployment"
    POST 
    <dcos_url>/service/kafka/v1/plans/deploy/interrupt?phase=Deployment HTTP/1.1

    {
        "Result": "Received cmd: interrupt"
    }

**Note:** The interrupt command can’t stop a step that is `InProgress`, but it will stop the change on the subsequent steps.

# Configuration Options

The following describes the most commonly used features of the Kafka service and how to configure them via the DC/OS CLI and from the DC/OS web interface. View the [default `config.json` in DC/OS Universe][11] to see all possible configuration options.

## Service Name

The name of this Kafka instance in DC/OS. This is an option that cannot be changed once the Kafka cluster is started: it can only be configured via the DC/OS CLI `--options` flag when the Kafka instance is created.

*   **In DC/OS CLI options.json**: `name`: string (default: `kafka`)
*   **DC/OS web interface**: The service name cannot be changed after the cluster has started.

## Broker Count

Configure the number of brokers running in a given Kafka cluster. The default count at installation is three brokers. This number may be increased, but not decreased, after installation.

*   **In DC/OS CLI options.json**: `broker-count`: integer (default: `3`)
*   **DC/OS web interface**: `BROKER_COUNT`: `integer`

## Broker Port

Configure the port number that the brokers listen on. If the port is set to a particular value, this will be the port used by all brokers. The default port is 9092.  Note that this requires that `placement-strategy` be set to `NODE` to take effect, since having every broker listening on the same port requires that they be placed on different hosts. Setting the port to 0 indicates that each Broker should have a random port in the 9092-10092 range.

*   **In DC/OS CLI options.json**: `broker-port`: integer (default: `9092`)
*   **DC/OS web interface**: `BROKER_PORT`: `integer`

## Configure Placement Constraints <!-- explained in template README.md ? -->


## Configure Kafka Broker Properties

Kafka Brokers are configured through settings in a server.properties file deployed with each Broker. The settings here can be specified at installation time or during a post-deployment configuration update. They are set in the DC/OS Universe's config.json as options such as:

    "log_retention_hours": {
        "title": "log.retention.hours",
        "description": "Override log.retention.hours: The number of hours to keep a log file before deleting it (in hours), tertiary to log.retention.ms property",
        "type": "integer",
        "default": 168
    },

The defaults can be overridden at install time by specifying an options.json file with a format like this:

    {
        "kafka": {
            "log_retention_hours": 100
        }
    }

These same values are also represented as environment variables for the scheduler in the form `KAFKA_OVERRIDE_LOG_RETENTION_HOURS` and may be modified through the DC/OS web interface and deployed during a rolling upgrade as [described here][12].

<a name="disk-type"></a>
## Disk Type

The type of disks that can be used for storing broker data are: `ROOT` (default) and `MOUNT`.  The type of disk may only be specified at install time.

* `ROOT`: Broker data is stored on the same volume as the agent work directory. Broker tasks will use the configured amount of disk space.
* `MOUNT`: Broker data will be stored on a dedicated volume attached to the agent. Dedicated MOUNT volumes have performance advantages and a disk error on these MOUNT volumes will be correctly reported to Kafka.

Configure Kafka service to use dedicated disk volumes:
* **DC/OS cli options.json**:

```json
    {
        "brokers": {
            "disk_type": "MOUNT"
        }
    }
```

* **DC/OS web interface**: Set the environment variable `DISK_TYPE`: `MOUNT`

When configured to `MOUNT` disk type, the scheduler selects a disk on an agent whose capacity is equal to or greater than the configured `disk` value.

## JVM Heap Size

Kafka service allows configuration of JVM Heap Size for the broker JVM process. To configure it:
* **DC/OS cli options.json**:

```json
    {
        "brokers": {
            "heap": {
                "size": 2000
            }
        }
    }
```

* **DC/OS web interface**: Set the environment variable `BROKER_HEAP_MB`: `2000`

**Note**: The total memory allocated for the Mesos task is specified by the `BROKER_MEM` configuration parameter. The value for `BROKER_HEAP_MB` should not be greater than `BROKER_MEM` value. Also, if `BROKER_MEM` is greater than `BROKER_HEAP_MB` then the Linux operating system will use `BROKER_MEM` - `BROKER_HEAP_MB` for [PageCache](https://en.wikipedia.org/wiki/Page_cache).


 [8]: #broker-count
 [11]: https://github.com/mesosphere/universe/tree/1-7ea/repo/packages/K/kafka/6
 [12]: #changing-configuration-at-runtime
