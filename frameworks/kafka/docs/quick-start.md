---
post_title: Quick Start
menu_order: 40
enterprise: 'no'
---

# Prerequisite

- [DC/OS installed on your cluster](https://docs.mesosphere.com/latest/administration/installing/).

1. Install a Kafka cluster. If you are using open source DC/OS, install a Kafka cluster with the following command from the DC/OS CLI. If you are using Enterprise DC/OS, you may need to follow additional instructions. See the Install and Customize section for more information.

   ```bash
   dcos package install beta-kafka
   ```

   Alternatively, you can install Kafka from [the DC/OS web interface](https://docs.mesosphere.com/latest/usage/webinterface/).

   Kafka will deploy with a default configuration. You can monitor deployment at the Services tab of the DC/OS web interface.

1. Create a new topic.

        ```bash
        $ dcos beta-kafka topic create topic1
        ```


1. Find Zookeeper and broker endpoint information.

        ```bash
        $ dcos beta-kafka endpoints zookeeper
        master.mesos:2181/dcos-service-kafka

        $ dcos beta-kafka endpoints broker
        {
          "address": [
            "10.0.3.226:1000",
            "10.0.3.98:1000",
            "10.0.0.120:1000"
          ],
          "dns": [
            "kafka-2-broker.kafka.autoip.dcos.thisdcos.directory:1000",
            "kafka-0-broker.kafka.autoip.dcos.thisdcos.directory:1000",
            "kafka-1-broker.kafka.autoip.dcos.thisdcos.directory:1000"
          ],
          "vip": "broker.kafka.l4lb.thisdcos.directory:9092"
        }
        ```

1. Produce and consume data.

        ```bash
        $ dcos node ssh --master-proxy --leader

        core@ip-10-0-6-153 ~ $ docker run -it mesosphere/kafka-client

        root@7d0aed75e582:/bin# echo "Hello, World." | ./kafka-console-producer.sh --broker-list kafka-0-broker.kafka.autoip.dcos.thisdcos.directory:1000, kafka-1-broker.kafka.autoip.dcos.thisdcos.directory:1000, kafka-2-broker.kafka.autoip.dcos.thisdcos.directory:1000 --topic topic1

        root@7d0aed75e582:/bin# ./kafka-console-consumer.sh --zookeeper master.mesos:2181/dcos-service-kafka --topic topic1 --from-beginning
        Hello, World.
        ```


See also [Connecting clients][1].

 [1]: https://docs.mesosphere.com/service-docs/kafka/connecting-clients/
