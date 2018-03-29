### Existing Features

* __Default Components__: Provide an extensible "default scheduler".

* __Multiple Pod Types__: Support services with heterogeneous nodes types - e.g., HDFS with Name Nodes, Journal Nodes, Data Nodes.

* __Configurable State Store Backing Service for Default Scheduler__: Enable configuring the state store backing service. Currently ZooKeeper is the only supported state store backing service.

* __Pod APIs__: Declarative pod specification via markup YAML API and Java API via the builder pattern.

* __Variable Substitution in YAML API__: Support variable substitution in YAML API - e.g., `type: {{TYPE}}`

* __Resources for Pods__: CPU and memory resources required for pods.

* __Local Storage Volume Resources for Pods__: Support for 'Root' and 'MOUNT' [persistent storage volumes](http://mesos.apache.org/documentation/latest/persistent-volume/) for stateful services.

* __Resource Reservation and Accounting for Pods__: Support for reserving pod resources. Reservations ensure that pods can always re-launch on a healthy agent with its local persistent storage volume (i.e. in cases of pods crash and agent reboot).

* __Task RUNNING Goal State for Pods__: Support long running tasks.

* __Binaries Assets for Tasks__: Define artifacts needed for the tasks - e.g., JRE, Kafka binaries.

* __Configuration Templating for Tasks__: Templating for task configuration files with [Mustache](https://mustache.github.io/) so that task configuration is customizable.

* __Configurable Environment Variables for Tasks__: Support configuring environment variables for tasks.

* __Scale-up/down CPU Resources for Pods__: Support increasing and decreasing CPU resources for a pod. Resource reservations are automatically updated accordingly.

* __Scale-up/down Memory Resources for Pods__: Support increasing and decreasing memory resources for a pod. Resource reservations are automatically updated accordingly.

* __Scale Out Count for Pods__: Support increasing the number of pods. If required by the service, operators must manually rebalance data after the additional pods are running.

* __Maintenance Plans__: Allow the definition and on-demand execution of plans with tasks that run inside the context of running pods. This allows for the definition of custom maintenance plans like backup and restore, data rebalance, etc.

* __Default Deploy Plan__: Built-in plan for deploying pods with both *serial* and *parallel* execution strategies.

* __Task FINISHED Goal State for Tasks within a Pod__: Support for tasks that run until finished. This is useful for operations such a formatting a data node volume.

* __Load Balanced Virtual Interface for Tasks__: Specify load balanced virtual interfaces for tasks. Virtual addresses define a name, source port, destination port mapping, protocol, and if it is externally advertised. Virtual addresses marked for external advertisement are discoverable via /endpoints HTTP API.

* __Placement constraints within Pods__: Support for Marathon constraints language for pod (with the exception of the Marathon constraint language for volumes).

* __Auto Suppress/Revive of Resource Offers for Default Scheduler__: Scheduler automatically sends SUPPRESS / REVIVE messages to Apache Mesos so that Apache Mesos only sends offers when the scheduler needs this. This enables Apache Mesos to scale much larger in terms of number of frameworks.

* __Set rlimits for Tasks__: Support for defining rlimit settings for a task.

* __Configurable Principal for Default Scheduler__: Enable configuring of the principal.

* __Set User Account for Tasks__: Support for setting the user account for a task.

* __Custom Strategies for Plans__: Java API with documentation for developing custom plan strategies.

* __Basic Docker Image Support for Tasks__: Support for a single Docker image per pod. (Note __Advanced Docker Image Support For Tasks__.)

* __Resource Sets__: Multiple different tasks can be run in sequence using the same resources (in particular persistent volumes) to produce a cumulative effect. For example, initialization tasks before main tasks can be modeled in this way.

* __Health Checks for Tasks__: Health check commands can be defined on a per task basis to automatically kill unhealthy tasks.

* __Readiness Checks for Tasks__: Readiness checks enable plans that restart pods to wait until data replication has caught up before proceeding.

* __Updatable Placement Constraints for Pods__: Enable operators to update pods placement constraints so that pods can safely replace without losing data or violating performance SLAs.

* __GPU Resources for Pods__: Support for [GPU resources](http://mesos.apache.org/documentation/latest/gpu-support/) to enable analytics workloads such as TensorFlow.

* __DC/OS Networking API Integration for Tasks__: Support for integrating with DC/OS Networking. DC/OS supports [Container Networking Interface](https://github.com/containernetworking/cni/blob/master/SPEC.md).

* __Enterprise DC/OS Secrets Management API__: Integrate with Enterprise DC/OS API for secure distribution of secrets such as certificates, keytab, and other sensitive files.

* __Enterprise DC/OS Role Based Access Control (RBAC) Integration__: With the Enterprise DC/OS Roles Based Access Control feature, ACLs are applied to Marathon folders to control access.

* __Graceful Shutdown for Tasks__: Support for graceful shutdown of services such as [Apache Kafka](https://kafka.apache.org/documentation#basic_ops_restarting). This is considered an optimization since hardware and software can fail unexpectedly.

* __Rack-aware Placement and Data Replication__: Many stateful services such as Elasticsearch, Kafka, Cassandra, and HDFS are "rack-aware". This feature depends on [Apache Mesos Fault Domains](https://docs.google.com/document/d/1gEugdkLRbBsqsiFv3urRPRNrHwUC-i1HwfFfHR_MvC8/edit).

* __Scheduler Metrics__: Instrument the default scheduler for performance, health, usage metrics for monitoring and troubleshooting. See [reporting](https://github.com/mesosphere/dcos-commons/blob/master/docs/pages/developer-guide.md#reporting) for details.

* __Decommision Pod Instances__: Enable the operator to decommission pod instance(s) aka reduce pod count to scale-in.

* __Independent Task Failure with a Pod__: Tasks in a pod can fail independently meaning they do not impact other tasks running in the same pod instance. Optionally tasks can be designated as 'essential'. When an essential task fails, all tasks in the pod instance are terminated.

### Planned Features

1. __Enable Application to Run Pods__: Enable applications including Spark Dispatcher, Metronome, and Jenkins to run tasks (pods) via a task queue.

1. __Maintenance API__: Apache Mesos offers [maintenance primitives](http://mesos.apache.org/documentation/latest/maintenance/) to notify the scheduler (framework) when cluster agents (servers) will be offline. With this feature, automated recovery plans could consider maintenance windows to make better decisions, and proactive task replacement plans will be possible.

1. __External Storage Volumes for Pods__: Add support for external storage volumes via Container Storage Interface (CSI).

1. __Read-only Volumes for Pods__: Support for read-only volumes with ([MESOS-4324](https://issues.apache.org/jira/browse/MESOS-4324)).

1. __FINISHED Goal State for Default Scheduler__: Currently services have an implied RUNNING goal state, which is appropriate for long running services. To support batch workloads such as TensorFlow, a new concept of a service goal state will be introduced with support for RUNNING and FINISHED goal states.

1. __Advanced Docker Image Support for Tasks__: Support for Docker image per task within a pod.

1. __Automated Failure Recovery for Tasks__: Failure guarantee is configurable on a pod-by-pod basis. Pods with a local persistent volume will default to the strongest guarantee (permanent), and pods without a local persistent volume will default to the lesser guarantee (transient). Makes uses of [MESOS-5344](https://issues.apache.org/jira/browse/MESOS-5344).

1. __HTTP/HTTPS Health Checks for Tasks__: Support for HTTP/HTTPS health checks with ([MESOS-2533](https://issues.apache.org/jira/browse/MESOS-2533)).

1. __TCP Health Checks for Tasks__: Support for TCP health checks with ([MESOS-3567](https://issues.apache.org/jira/browse/MESOS-3567)).
