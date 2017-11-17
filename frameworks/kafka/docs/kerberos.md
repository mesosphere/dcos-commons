# Setting up Kafka with Kerberos

Still TODO:
1. We need a better way to get the Keytab into the clients

## Deploy KDC
Create a file (e.g. `kafka-principals.txt`) contiaining a list of principals required for Kafka. For example:
```
kafka/kafka-0-broker.secure-kafka.autoip.dcos.thisdcos.directory@LOCAL
kafka/kafka-1-broker.secure-kafka.autoip.dcos.thisdcos.directory@LOCAL
kafka/kafka-2-broker.secure-kafka.autoip.dcos.thisdcos.directory@LOCAL
client@LOCAL
```

Note that this assumes that `kafka` will be deployed with 3 brokers and with as service name of `secure-kafka`. An additional principal `client@LOCAL` has also been added for client authentication.

Run the KDC utility to setup the KDC (this assume that you're in the `dcos-commons` root folder):
```bash
$ PYTHONPATH=testing ./tools/kdc.py deploy kafka-principals.txt
```
(where `kafka-principals.txt` is the file created in the first step)

*Note:* this assumes that the environement is set up to be able to run the SDK integration test suite. If this is not the case, the `./test.sh -i` interactive mode can be used.

This performs the following actions:
1. Deploys a KDC Marathon application named `kdc`
1. Adds the principals in `kafka-principals.txt` to the KDC store
1. Saves the generated keytab as the DC/OS secret `__dcos_base64___keytab`

## Deploy Kerberized kafka

Create the following `kerberos-options.json` file:
```json
{
    "service": {
        "name": "secure-kafka",
        "security": {
            "kerberos": {
                "enabled": true,
                "kdc_host_name": "kdc.marathon.autoip.dcos.thisdcos.directory",
                "kdc_host_port": 2500,
                "keytab_secret": "__dcos_base64___keytab"
            }
        }
    }
}
```
(Here `kdc.marathon.autoip.dcos.thisdcos.directory` and `2500` are the IP and port of the KDC Marathon app)

and deploy Apache Kafka:
```bash
$ dcos package install beta-kafka --yes --options=kerberos-options.json
```

This should show the Kafka tasks starting up:
```bash
$ dcos task
NAME            HOST         USER   STATE  ID                                                    MESOS ID
kafka-0-broker  10.0.3.129  nobody    R    kafka-0-broker__6cd0d1fe-c72f-4725-aebe-0e88e9ec74ed  83acb270-f32a-408a-9548-26b0d2f2b95f-S2
kafka-1-broker  10.0.2.202  nobody    R    kafka-1-broker__1bbd14aa-5b66-435a-9d11-1777bb80c88a  83acb270-f32a-408a-9548-26b0d2f2b95f-S1
kafka-2-broker  10.0.2.91   nobody    R    kafka-2-broker__a2975665-a21d-4882-99f5-80da5b55d1a6  83acb270-f32a-408a-9548-26b0d2f2b95f-S4
kdc             10.0.0.245   root     R    kdc.0128cb11-c3ef-11e7-821b-7e246f9e43a9              83acb270-f32a-408a-9548-26b0d2f2b95f-S3
secure-kafka    10.0.0.145   root     R    secure-kafka.53b7ef94-c3f0-11e7-821b-7e246f9e43a9     83acb270-f32a-408a-9548-26b0d2f2b95f-S0
```
and show the logs:
```bash
$ dcos task log kafka-0
[2017-11-07 19:18:41,149] INFO Successfully authenticated client: authenticationID=kafka/kafka-0-broker.secure-kafka.autoip.dcos.thisdcos.directory@LOCAL; authorizationID=kafka/kafka-0-broker.secure-kafka.autoip.dcos.thisdcos.directory@LOCAL. (org.apache.kafka.common.security.authenticator.SaslServerCallbackHandler)
[2017-11-07 19:18:41,188] INFO Setting authorizedID: kafka (org.apache.kafka.common.security.authenticator.SaslServerCallbackHandler)
```

## Testing with a Kerberized client

### Using the pre-built client
Starting a Marathon app with the following definition:
```json
{
    "id": "kafka-producer",
    "mem": 512,
    "user": "nobody",
    "container": {
        "type": "MESOS",
        "docker": {
            "image": "elezar/kafka-client:latest",
            "forcePullImage": true
        },
        "volumes": [
            {
                "containerPath": "/tmp/kafkaconfig/kafka-client.keytab",
                "secret": "kafka_keytab"
            }
        ]
    },
    "secrets": {
        "kafka_keytab": {
            "source": "__dcos_base64___keytab"
        }
    },
    "networks": [
        {
            "mode": "host"
        }
    ],
    "env": {
        "JVM_MaxHeapSize": "512",
        "KAFKA_CLIENT_MODE": "producer"
    }
}
```
Will start a Kafka console producer which publishes a message on the `securetest` topic every 10 seconds.

Running:
```bash
$ dcos task log kafka-producer
```
should show the messages being written to the topic.

Changing the `KAFAK_CLIENT_MODE` environment variable to `consumer` (and adjusting the name accordingly) will start a Kafka console consumer subscribed to the same `securetest` topic.

### Building your own client
In order to configure a Kerberized Kafka client, three things are needed:
1. The Kerberos keytab as a file `kafka-client.keytab` (This can be downloaded from the KDC application)
1. A file `client-jaas.conf` containing:
    ```
    KafkaClient {
        com.sun.security.auth.module.Krb5LoginModule required
        doNotPrompt=true
        useTicketCache=true
        principal="client@LOCAL"
        useKeyTab=true
        serviceName="kafka"
        keyTab="/tmp/kafkaconfig/kafka-client.keytab"
        client=true;
    };
    ```
1. A file `krb5.conf` containing (TODO: This could be simplified):
    ```
    [logging]
        default = STDERR
        kdc = STDERR
        admin_server = STDERR

    [libdefaults]
        default_realm = LOCAL
        dns_lookup_realm = false
        dns_lookup_kdc = false
        ticket_lifetime = 24h
        renew_lifetime = 7d
        forwardable = true

    [realms]
        LOCAL = {
            kdc = kdc.marathon.autoip.dcos.thisdcos.directory:2500
        }

    [domain_realm]
        .secure-kafka.autoip.dcos.thisdcos.directory = LOCAL
        secure-kafka.autoip.dcos.thisdcos.directory = LOCAL
    ```
1. A file `client.properties` containing:
    ```
    security.protocol=SASL_PLAINTEXT
    sasl.mechanism=GSSAPI
    sasl.kerberos.service.name=kafka
    ```

For these examples, it is assumed that these files are in the folder `/tmp/kafkaconfig` on one of the nodes of the DC/OS cluster and that we have connected to the node as follows:
```bash
$ dcos node ssh --master-proxy --mesos-id=83acb270-f32a-408a-9548-26b0d2f2b95f-S4
```

### Download the JCE:
```bash
$ cd /tmp/kafkaconfig && \
    wget https://downloads.mesosphere.com/java/jre-8u131-linux-x64-jce-unlimited.tar.gz && \
    tar -xzvf jre-8u131-linux-x64-jce-unlimited.tar.gz && \
    rm jre-8u131-linux-x64-jce-unlimited.tar.gz
```

### Launch a Docker container containing the Confluent Kafka Client:
```bash
$ docker run --rm -ti \
    -v /tmp/kafkaconfig:/tmp/kafkaconfig:ro \
    -e KAFKA_OPTS="-Djava.security.auth.login.config=/tmp/kafkaconfig/client-jaas.conf -Djava.security.krb5.conf=/tmp/kafkaconfig/krb5.conf -Dsun.security.krb5.debug=true" \
    -e JAVA_HOME="/tmp/kafkaconfig/jre1.8.0_131 \
    confluentinc/cp-kafka:3.3.0 \
    bash
```
Note that we set the JAAS config as well as the Kerberos Java options to point to the files that were created in the previous step.

### Run the Kafka producer:
```bash
$ echo "This is a secure test at $(date)" | kafka-console-producer --broker-list kafka-0-broker.secure-kafka.autoip.dcos.thisdcos.directory:1025,kafka-1-broker.secure-kafka.autoip.dcos.thisdcos.directory:1025,kafka-2-broker.secure-kafka.autoip.dcos.thisdcos.directory:1025 \
    --topic securetest \
    --producer.config /tmp/kafkaconfig/client.properties
```

### Run the Kafa consumer:
```bash
$ kafka-console-consumer --bootstrap-server kafka-0-broker.secure-kafka.autoip.dcos.thisdcos.directory:1025 \
    --topic securetest --from-beginning \
    --consumer.config /tmp/kafkaconfig/client.properties
```


# TODO: Document raw instructions:

Which assumes that kdc is running on the host `10.0.0.95`. It also assumes that the keytab for the brokers has been generated and the secret added as follows:
```bash
$ dcos task exec kdc /usr/sbin/kadmin -l add --use-defaults --random-password kafka/kafka-0-broker.secure-kafka.autoip.dcos.thisdcos.directory@LOCAL kafka/kafka-1-broker.secure-kafka.autoip.dcos.thisdcos.directory@LOCAL kafka/kafka-1-broker.secure-kafka.autoip.dcos.thisdcos.directory@LOCAL
```
```bash
$ dcos task exec kdc /usr/sbin/kadmin -l ext -k kafka.keytab kafka/kafka-0-broker.secure-kafka.autoip.dcos.thisdcos.directory@LOCAL kafka/kafka-1-broker.secure-kafka.autoip.dcos.thisdcos.directory@LOCAL kafka/kafka-2-broker.secure-kafka.autoip.dcos.thisdcos.directory@LOCAL
```

* Download the keytab file

```bash
$ base64 -w 0 kafka.keytab > kafka.keytab.base64
```

```bash
$ dcos security secrets create __dcos_base64__kafka_keytab --value-file kafka.keytab.base64
```
