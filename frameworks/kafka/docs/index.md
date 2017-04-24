---
post_title: Kafka
menu_order: 90
feature_maturity: preview
enterprise: 'no'
---

DC/OS Apache Kafka is an automated service that makes it easy to deploy and manage Apache Kafka on Mesosphere DC/OS, eliminating nearly all of the complexity traditionally associated with managing a Kafka cluster. Apache Kafka is a distributed high-throughput publish-subscribe messaging system with strong ordering guarantees. Kafka clusters are highly available, fault tolerant, and very durable. For more information on Apache Kafka, see the Apache Kafka [documentation][1]. DC/OS Kafka gives you direct access to the Kafka API so that existing producers and consumers can interoperate. You can configure and install DC/OS Kafka in moments. Multiple Kafka clusters can be installed on DC/OS and managed independently, so you can offer Kafka as a managed service to your organization.


# Benefits

DC/OS Kafka offers the following benefits of a semi-managed service:

*   Easy installation
*   Multiple Kafka clusters
*   Elastic scaling of brokers
*   Replication for high availability
*   Kafka cluster and broker monitoring

# Features

DC/OS Kafka provides the following features:

*   Single-command installation for rapid provisioning
*   Multiple clusters for multiple tenancy with DC/OS
*   High availability runtime configuration and software updates
*   Storage volumes for enhanced data durability, known as Mesos Dynamic Reservations and Persistent Volumes
*   Integration with syslog-compatible logging services for diagnostics and troubleshooting
*   Integration with statsd-compatible metrics services for capacity and performance monitoring

# Related Services

*   [DC/OS Spark][2]

 [1]: http://kafka.apache.org/documentation.html
 [2]: https://docs.mesosphere.com/service-docs/spark/
