---
post_title: Quick Start
menu_order: 0
feature_maturity: preview
enterprise: 'no'
---











1. Install a Kafka cluster. If you are using open source DC/OS, install a Kafka cluster with the following command from the DC/OS CLI. If you are using Enterprise DC/OS, you may need to follow additional instructions. See the Install and Customize section for more information.
    
        $ dcos package install kafka
        

1. Create a new topic.
    
        $ dcos kafka topic create topic1
        

1. Find connection information.
    
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
        

1. Produce and consume data.
    
        $ dcos node ssh --master-proxy --leader
        
        core@ip-10-0-6-153 ~ $ docker run -it mesosphere/kafka-client
        
        root@7d0aed75e582:/bin# echo "Hello, World." | ./kafka-console-producer.sh --broker-list broker-0.kafka.mesos:9843, broker-1.kafka.mesos:10056, broker-2.kafka.mesos:9689 --topic topic1
        
        root@7d0aed75e582:/bin# ./kafka-console-consumer.sh --zookeeper master.mesos:2181/dcos-service-kafka --topic topic1 --from-beginning
        Hello, World.
        

See also [Connecting clients][1].

 [1]: https://docs.mesosphere.com/1.9/usage/service-guides/kafka/connecting-clients
