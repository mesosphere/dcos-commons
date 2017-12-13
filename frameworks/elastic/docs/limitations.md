---
post_title: Limitations
menu_order: 100
enterprise: 'no'
---

## Node requirements

The maximum number of deployable nodes is constrained by the DC/OS cluster's resources. Each Elasticsearch node has specified required resources, so nodes may not be placed if the DC/OS cluster lacks the requisite resources.

## Upgrades and rolling configuration updates do not wait for green status

During deployment and upgrades, the `serial` strategy does not wait for the Elastic service to reach green before proceeding to the next node.

## Out-of-band configuration

Out-of-band configuration modifications are not supported. The service's core responsibility is to deploy and maintain the service with a specified configuration. In order to do this, the service assumes that it has ownership of task configuration. If an end-user makes modifications to individual tasks through out-of-band configuration operations, the service will override those modifications at a later time. For example:
- If a task crashes, it will be restarted with the configuration known to the scheduler, not one modified out-of-band. 
- If a configuration update is initiated, all out-of-band modifications will be overwritten during the rolling update.

ElasticSearch provides two ways of updating settings: persistent (through `elasticsearch.yml` file) and transient (through Elastic Settings Update API). The service's Configuration Options are carried over to the tasks' elasticsearch.yml file automatically. Out-of-band configuration changes (either via ElasticSearch's Update API or externally modifying elasticsearch.yml files) will not persist in case of a restart, failure recovery, or upgrade.  

## Scaling in

To prevent accidental data loss, the service does not support reducing the number of pods.

## Disk changes

To prevent accidental data loss from reallocation, the service does not support changing volume requirements after initial deployment.

## Best-effort installation

If your cluster doesn't have enough resources to deploy the service as requested, the initial deployment will not complete until either those resources are available or until you reinstall the service with corrected resource requirements. Similarly, scale-outs following initial deployment will not complete if the cluster doesn't have the needed available resources to complete the scale-out.

## Virtual networks

When the service is deployed on a virtual network, the service may not be switched to host networking without a full re-installation. The same is true for attempting to switch from host to virtual networking.

## Task Environment Variables

Each service task has some number of environment variables, which are used to configure the task. These environment variables are set by the service scheduler. While it is _possible_ to use these environment variables in adhoc scripts (e.g. via `dcos task exec`), the name of a given environment variable may change between versions of a service and should not be considered a public API of the service.