---
post_title: Elastic
menu_order: 65
feature_maturity: experimental
enterprise: 'no'
---

DC/OS Elastic is an automated service that makes it easy to deploy and manage Elastic 5 and Kibana 5 with X-Pack on Mesosphere DC/OS, eliminating nearly all of the complexity traditionally associated with managing an Elasticsearch cluster. Elasticsearch is a distributed, multitenant-capable, full-text search engine with an HTTP web interface and schema-free JSON documents. Elasticsearch clusters are highly available, fault tolerant, and durable. For more information on Elasticsearch, Kibana, and X-Pack, visit the [Elastic](https://www.elastic.co/) site. Multiple Elasticsearch clusters can be installed on DC/OS and managed independently, so you can offer Elasticsearch as a managed service to your organization.

# Benefits

DC/OS Elastic offers the following benefits of a semi-managed service:

*   Easy installation
*   Elastic scaling of nodes
*   Replication for high availability
*   Elasticsearch cluster and node monitoring

# Features

DC/OS Elastic provides the following features:

*   Single-command installation for rapid provisioning
*   Multiple clusters for multiple tenancy with DC/OS
*   High availability runtime configuration and software updates
*   Storage volumes for enhanced data durability, known as Mesos Dynamic Reservations and Persistent Volumes
*   Automatic reporting of Elasticsearch metrics to DC/OS statsd collector
