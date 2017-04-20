---
post_title: Connecting Clients
menu_order: 40
feature_maturity: preview
enterprise: 'no'
---

The only supported client library is the official Kafka Java library, i.e., `org.apache.kafka.clients.consumer.KafkaConsumer` and `org.apache.kafka.clients.producer.KafkaProducer`. Other clients are at the user's risk.

# Kafka Client API Compatibility

1. The Kafka client protocol is versioned and the cluster supports multiple versions.
2. Kafka is backwards compatible: Newer versions of Kafka always continue to support older versions of the protocol. The implication of this is older clients continue to work with newer versions of Kafka.
3. Clients are not forward compatible: There is no effort to have newer versions of the client support older versions of Kafka the protocol. The implication of this is newer clients are not compatible older versions of Kafka.

# Using the DC/OS CLI

The following command can be executed from the cli in order to retrieve a set of brokers to connect to.

    dcos kafka --name=<name> endpoints broker

<a name="using-the-rest-api"></a>
# Using the REST API

REST API requests must be authenticated. See the REST API Authentication part of the REST API Reference for more information.

The following `curl` example demonstrates how to retrive connection a set of brokers to connect to using the REST API.

    curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/endpoints/broker"

## User token authentication

DC/OS Enterprise Edition comes with support for [user ACLs][13]. To interact with the Kafka REST API you must first retrieve an auth token from the [auth HTTP endpoint][14], then provide this token in following requests.

First, we retrieve `uSeR_t0k3n` with our user credentials and store the token as an environment variable:

    curl --data '{"uid":"username", "password":"password"}' -H "Content-Type:application/json" "<dcos_url>/acs/api/v1/auth/login"

    {
      "token": "uSeR_t0k3n"
    }

    export auth_token=uSeR_t0k3n


Then, use this token to authenticate requests to the Kafka Service:

    curl -H "Authorization: token=$auth_token" "<dcos_url>/service/kafka/v1/endpoints/broker"

You do not need the token to access the Kafka brokers themselves.

# Connection Info Response

The response, for both the CLI and the REST API is as below.

  {
    "address": [
      "10.0.0.49:1025",
      "10.0.2.253:1025",
      "10.0.1.27:1025"
    ],
    "dns": [
      "kafka-2-broker.kafka.mesos:1025",
      "kafka-0-broker.kafka.mesos:1025",
      "kafka-1-broker.kafka.mesos:1025"
    ],
    "vip": "broker.kafka.l4lb.thisdcos.directory:9092"
  }

This JSON array contains a list of valid brokers that the client can use to connect to the Kafka cluster. For availability reasons, it is best to specify multiple brokers in configuration of the client. Use the VIP to address any one of the Kafka brokers in the cluster. [Learn more about load balancing and VIPs in DC/OS](https://docs.mesosphere.com/1.9/networking/).

# Configuring the Kafka Client Library

## Adding the Kafka Client Library to Your Application

    <dependency>
      <groupId>org.apache.kafka</groupId>
      <artifactId>kafka-clients</artifactId>
      <version>0.9.0.1</version>
    </dependency>


The above is the correct dependency for the Kafka Client Library to use with the DC/OS Kafka service. After adding this dependency to your project, you should have access to the correct binary dependencies to interface with the Kafka Cluster.

## Connecting the Kafka Client Library

The code snippet below demonstrates how to connect a Kafka Producer to the cluster and perform a loop of simple insertions.

    import org.apache.kafka.clients.producer.KafkaProducer;
    import org.apache.kafka.clients.producer.ProducerRecord;
    import org.apache.kafka.common.serialization.ByteArraySerializer;

    Map<String, Object> producerConfig = new HashMap<>();
    producerConfig.put("bootstrap.servers", "10.0.0.211:9843,10.0.0.217:10056,10.0.0.214:9689");
    // optional:
    producerConfig.put("metadata.fetch.timeout.ms": "3000");
    producerConfig.put("request.timeout.ms", "3000");
    // ... other options: http://kafka.apache.org/documentation.html#producerconfigs
    ByteArraySerializer serializer = new ByteArraySerializer();
    KafkaProducer<byte[], byte[]> kafkaProducer = new KafkaProducer<>(producerConfig, serializer, serializer);

    byte[] message = new byte[1024];
    for (int i = 0; i < message.length; ++i) {
      if (i % 2 == 0) {
        message[i] = 'x';
      } else {
        message[i] = 'o';
      }
    }
    ProducerRecord<byte[], byte[]> record = new ProducerRecord<>("test-topic", message);
    while (true) {
      kafkaProducer.send(record).get();
      Thread.sleep(1000);
    }


The code snippet below demonstrates how to connect a Kafka Consumer to the cluster and perform a simple retrievals.

    import org.apache.kafka.clients.consumer.ConsumerRecord;
    import org.apache.kafka.clients.consumer.ConsumerRecords;
    import org.apache.kafka.clients.consumer.KafkaConsumer;
    import org.apache.kafka.common.serialization.ByteArrayDeserializer;

    Map<String, Object> consumerConfig = new HashMap<>();
    consumerConfig.put("bootstrap.servers", "10.0.0.211:9843,10.0.0.217:10056,10.0.0.214:9689");
    // optional:
    consumerConfig.put("group.id", "test-client-consumer")
    // ... other options: http://kafka.apache.org/documentation.html#consumerconfigs
    ByteArrayDeserializer deserializer = new ByteArrayDeserializer();
    KafkaConsumer<byte[], byte[]> kafkaConsumer = new KafkaConsumer<>(consumerConfig, deserializer, deserializer);

    List<String> topics = new ArrayList<>();
    topics.add("test-topic");
    kafkaConsumer.subscribe(topics);
    while (true) {
      ConsumerRecords<byte[], byte[]> records = kafkaConsumer.poll(1000);
      int bytes = 0;
      for (ConsumerRecord<byte[], byte[]> record : records) {
        if (record.key() != null) {
          bytes += record.key().length;
        }
        bytes += record.value().length;
      }
      System.out.println(String.format("Got %d messages (%d bytes)", records.count(), bytes));
    }
    kafkaConsumer.close();


# Configuring the Kafka Test Scripts

The following code connects to a DC/OS-hosted Kafka instance using `bin/kafka-console-producer.sh` and `bin/kafka-console-consumer.sh` as an example:

    dcos kafka endpoints broker
    {
      "address": [
        "10.0.0.49:1025",
        "10.0.2.253:1025",
        "10.0.1.27:1025"
      ],
      "dns": [
        "kafka-2-broker.kafka.mesos:1025",
        "kafka-0-broker.kafka.mesos:1025",
        "kafka-1-broker.kafka.mesos:1025"
      ],
      "vip": "broker.kafka.l4lb.thisdcos.directory:9092"
    }

    dcos node ssh --master-proxy --leader

    core@ip-10-0-6-153 ~ docker run -it mesosphere/kafka-client

    root@7d0aed75e582:/bin# echo "Hello, World." | ./kafka-console-producer.sh --broker-list 10.0.0.49:1025, 10.0.2.253:1025, 10.0.1.27:1025 --topic topic1

    root@7d0aed75e582:/bin# ./kafka-console-consumer.sh --zookeeper master.mesos:2181/kafka --topic topic1 --from-beginning
    Hello, World.

 [13]: https://docs.mesosphere.com/1.9/security/users-groups/
 [14]: https://docs.mesosphere.com/1.9/security/iam-api/
