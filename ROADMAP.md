### Existing Features

1. __Default Components__: Provide an extensible "default scheduler" to simplify building frameworks.

1. __Multiple Pod Types__: Support services with Heterogeneous nodes types - e.g., HDFS with Name Nodes, Journal Nodes, Data Nodes.

1. __Configurable State Store Backing Service for Default Scheduler__: Enable configuring the state store backing service. Currently ZooKeeper is the only supported state store backing service.

1. __Pod APIs__: Declarative pod specification via markup YAML API and Java API via the builder pattern.

1. __Variable Substitution in YAML API__: Support variable substitution in YAML API - e.g., `type: {{TYPE}}`

1. __Count for Pods__: Support for declaring the number of instances of a pod that should be running.

1. __Resources for Pods__: CPU and memory resources required for pods.

1. __Local Storage Volume Resources for Pods__: Support for 'Root' and 'MOUNT' [persistent storage volumes](http://mesos.apache.org/documentation/latest/persistent-volume/) for stateful services.

1. __Resource Reservation and Accounting for Pods__: Support for reserving pod resources. Reservations ensure that pods can always re-launch on a healthy agent with its local persistent storage volume (i.e. in cases of pods crash and agent reboot).

1. __Task RUNNING Goal State for Pods__: Support long running tasks.

1. __Binaries Assets for Tasks__: Define artifacts needed for the tasks - e.g., JRE, Kafka binaries.

1. __Configuration Templating for Tasks__: Templating for task configuration files with [Mustache](https://mustache.github.io/) so that task configuration is customizable.

1. __Configurable Environment Variables for Tasks__: Support configuring environment variables for tasks.

1. __Scale-up/down CPU Resources for Pods__: Support increasing and deceasing CPU resources for a pod. Resource reservations are automatically updated accordingly.

1. __Scale-up/down Memory Resources for Pods__: Support increasing and deceasing memory resources for a pod. Resource reservations are automatically updated accordingly.

1. __Scale Out Count for Pods__: Support increasing the number of pods. If required by the service, operators must manually rebalance data after the additional pods are running.

1. __Maintenance Plans__: Allow the definition and on-demand execution of plans with tasks that run inside the context of running pods. This allows for the definition of custom maintenance plans like backup and restore, data rebalance, etc.

1. __Default Deploy Plan__: Built-in plan for deploying pods with both *serial* and *parallel* execution strategies.

1. __Task FINISHED Goal State for Tasks within a Pod__: Support for tasks that run until finished. This is useful for operations such a formatting a data node volume.

1. __Load Balanced Virtual Interface for Tasks__: Specify load balanced virtual interfaces for tasks. Virtual addresses define a name, source port, destination port mapping, protocol, and if it is externally advertised. Virtual addresses marked for external advertisement are discoverable via /endpoints HTTP API.

1. __Placement Constraints within Pods__: Support for Marathon constrains language for pod (with the exception of the Marathon constrain language for volumes).

1. __Auto Suppress/Revive of Resource Offers for Default Scheduler__: Scheduler automatically sends SUPPRESS / REVIVE messages to Apache Mesos so that Apache Mesos only sends offers when the scheduler needs this. This enables Apache Mesos to scale much larger in terms of number of frameworks.

1. __Set rlimits for Tasks__: Support for defining rlimit settings for a task.

1. __Configurable Principal for Default Scheduler__: Enable configuring of the principal.

1. __Set User Account for Tasks__: Support for setting the user account for a task.

1. __Custom Strategies for Plans__: Java API with documentation for developing custom plan strategies.

1. __DC/OS Networking API Integration for Tasks__: Support for integrating with DC/OS Networking. DC/OS supports [Container Networking Interface](https://github.com/containernetworking/cni/blob/master/SPEC.md).

1. __Command Health Checks for Tasks__: Support for command health checks for tasks.

1. __Basic Docker Image Support for Tasks__: Support for a single Docker image per pod.

1. __Resource Sets__: Multiple different tasks can be run in sequence using the same resources (in particular persistent volumes) to produce a cumulative effect.  For example. initialization tasks before main tasks can be modeled in this way.

1. __Health Checks for Tasks__: Health check commands can be defined on a per Task basis to automatically kill unhealthy tasks.

1. __Readiness Checks for Tasks__: Add support for task readiness checks. Readiness checks enable configuration and software updates plans that restart pods to wait until data replication has caught up before proceeding.

### Planned Features

1. __Advanced Docker Image Support for Tasks__: Support for Docker image per tasks.

1. __Enterprise DC/OS Secrets Management API__: Integrate with Enterprise DC/OS API for secure distribution of secrets such as certificates, keytab, and other sensitive files.

1. __Enterprise DC/OS Role Based Access Control (RBAC) Integration__: With the Enterprise DC/OS Roles Based Access Control feature, ACLs are applied to Marathon folders to control access.

1. __Scheduler and Executor Metrics__: Instrument the default scheduler and executor to send performance / health / usage metrics for monitoring and troubleshooting via [DC/OS Metrics](https://github.com/dcos/dcos-metrics).

1. __Graceful Shutdown for Tasks__: Support for graceful shutdown of services such as [Apache Kafka](https://kafka.apache.org/documentation#basic_ops_restarting). This is considered an optimization since hardware and software can fail unexpectedly.

1. __Map Agent Attributes to Racks for Pods__: Many stateful services such as Elasticsearch, Kafka, Cassandra, and HDFS are "rack-aware". This feature is to support mapping a DC/OS Agent attribute to rack configuration.

1. __Updatable Placement Constraints for Pods__: Enable operators to update pods placement constraints so that pods can safely replace without losing data or violating performance SLAs.

1. __Partition-Aware API Integration__: Support for Apache Mesos partition-aware frameworks APIs. For more details see [MESOS-5344](https://issues.apache.org/jira/browse/MESOS-5344) and [MESOS-6394](https://issues.apache.org/jira/browse/MESOS-6394).

1. __Non-reserved Resources for Tasks__: Currently the SDK always reserves resources. This is because the initial focus for the SDK is stateful workloads, where reserving resources in essential for safe operations. For other workloads like twelve-factor apps and analytics jobs, reserving resources is generally undesirable.

1. __Scale-in Count for Pods__: Reducing node (pod) count for most data services requires draining data. The SDK currently prevents reducing the pod count to avoid accidental deletion of data. With this feature, a plan could be called to drain data prior to termination. On termination, resources for the pods will be reserved. Note: scale-out, scale-up, scale-down are already supported.

1. __Read-only Volumes for Pods__: Support for read-only volumes with ([MESOS-4324](https://issues.apache.org/jira/browse/MESOS-4324)).

1. __HTTP/HTTPS Health Checks for Tasks__: Support for HTTP/HTTPS health checks with ([MESOS-2533](https://issues.apache.org/jira/browse/MESOS-2533)).

1. __TCP Health Checks for Tasks__: Support for TCP health checks with ([MESOS-3567](https://issues.apache.org/jira/browse/MESOS-3567)).

1. __External Storage Volumes for Pods__: Add support for external storage volumes via a standard API - e.g., Container Volume Interface (CVI).

1. __Enterprise DC/OS Service Account Configuration__: Wrap the [process](https://docs.mesosphere.com/latest/administration/id-and-access-mgt/service-auth/custom-service-auth) of configuring an Enterprise DC/OS Service Account.

1. __Maintenance API__: Apache Mesos offers [maintenance primitives](http://mesos.apache.org/documentation/latest/maintenance/) to notify the scheduler (framework) when cluster agents (servers) will be offline. With this feature, automated recovery plans could consider maintenance windows to make better decisions, and proactive task replacement plans will be possible.

1. __GPU Resources for Pods__: Support for [GPU resources](http://mesos.apache.org/documentation/latest/gpu-support/) to enable analytics workloads such as TensorFlow.

1. __FINISHED Goal State for Default Scheduler__: Currently services have an implied RUNNING goal state, which is appropriate for long running services. To support batch workloads such as TensorFlow, a new concept of a service goal state will be introduced with support for RUNNING and FINISHED goal states.
