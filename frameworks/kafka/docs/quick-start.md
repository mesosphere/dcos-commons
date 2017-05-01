---
post_title: Kick the Tires
menu_order: 10
feature_maturity: preview
enterprise: 'no'
---











1. Install a Kafka cluster. If you are using open source DC/OS, install a Kafka cluster with the following command from the DC/OS CLI. If you are using Enterprise DC/OS, you may need to follow additional instructions. See the Install and Customize section for more information.

        dcos package install kafka


1. Create a new topic.

        dcos kafka topic create topic1


1. Find Zookeeper and broker endpoint information.

        dcos kafka endpoints zookeeper
        master.mesos:2181/dcos-service-kafka

        dcos kafka endpoints broker
        {
          "address": [
            "10.0.3.226:1025",
            "10.0.3.98:1025",
            "10.0.0.120:1025"
          ],
          "dns": [
            "kafka-2-broker.kafka.mesos:1025",
            "kafka-0-broker.kafka.mesos:1025",
            "kafka-1-broker.kafka.mesos:1025"
          ],
          "vips": [
            "broker.kafka.l4lb.thisdcos.directory:9092"
          ]
        }

1. Produce and consume data.

        dcos node ssh --master-proxy --leader

        core@ip-10-0-6-153 ~ docker run -it mesosphere/kafka-client

        root@7d0aed75e582:/bin# echo "Hello, World." | ./kafka-console-producer.sh --broker-list kafka-0-broker.kafka.mesos:1025, kafka-1-broker.kafka.mesos:1025, kafka-2-broker.kafka.mesos:1025 --topic topic1

        root@7d0aed75e582:/bin# ./kafka-console-consumer.sh --zookeeper master.mesos:2181/dcos-service-kafka --topic topic1 --from-beginning
        Hello, World.


See also [Connecting clients][1].

 [1]: https://docs.mesosphere.com/service-docs/kafka/connecting-clients/
