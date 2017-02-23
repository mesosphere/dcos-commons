---
post_title: Limitations
menu_order: 50
feature_maturity: experimental
enterprise: 'no'
---

# Managing Configurations Outside of the Service

The Elasticsearch service's core responsibility is to deploy and maintain the deployment of an Elasticsearch cluster whose configuration has been specified. In order to do this, the service makes the assumption that it has ownership of node configuration. If an end-user makes modifications to individual nodes through out-of-band configuration operations, the service will almost certainly override those modifications at a later time. If a node crashes, it will be restarted with the configuration known to the scheduler, not with one modified out-of-band. If a configuration update is initiated, all out-of-band modifications will be overwritten during the rolling update.

# Nodes

The maximum number of deployable nodes is constrained by the DC/OS cluster's resources. Each Elasticsearch node has specified required resources, so nodes may not be placed if the DC/OS cluster lacks the requisite resources.
