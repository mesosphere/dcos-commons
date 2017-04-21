---
post_title: Limitations
menu_order: 80
feature_maturity: preview
enterprise: 'no'
---








* Configurations

    The "disk" configuration value is denominated in MB. We recommend you set the configuration value `log_retention_bytes` to a value smaller than the indicated "disk" configuration. See the Configuring section for instructions for customizing these values.

* Managing Configurations Outside of the Service

    The Kafka service's core responsibility is to deploy and maintain the deployment of a Kafka cluster whose configuration has been specified. In order to do this the service makes the assumption that it has ownership of broker configuration. If an end-user makes modifications to individual brokers through out-of-band configuration operations, the service will almost certainly override those modifications at a later time. If a broker crashes, it will be restarted with the configuration known to the scheduler, not one modified out-of-band. If a configuration update is initiated, all out-of-band modifications will be overwritten during the rolling update.

* Brokers

    The number of deployable brokers is constrained by two factors. First, brokers have specified required resources, so brokers may not be placed if the DC/OS cluster lacks the requisite resources. Second, the specified "PLACEMENT_STRATEGY" environment variable may affect how many brokers can be created in a Kafka cluster. By default the value is "ANY," so brokers are placed anywhere and are only constrained by the resources of the cluster. A second option is "NODE." In this case only one broker may be placed on a given DC/OS agent.

* Security

    The security features introduced in Apache Kafka 0.9 are not supported at this time.
