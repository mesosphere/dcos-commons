---
post_title: Limitations
menu_order: 100
enterprise: 'no'
---

_MANAGE CUSTOMER EXPECTIONS BY DISCLOSING ANY FEATURES OF YOUR PRODUCT THAT ARE NOT SUPPORTED WITH DC/OS, FEATURES MISSING FROM THE DC/OS INTEGRATION, ETC._

## Out-of-band configuration

Out-of-band configuration modifications are not supported. The service's core responsibility is to deploy and maintain the service with a specified configuration. In order to do this, the service assumes that it has ownership of task configuration. If an end-user makes modifications to individual tasks through out-of-band configuration operations, the service will override those modifications at a later time. For example:
- If a task crashes, it will be restarted with the configuration known to the scheduler, not one modified out-of-band.
- If a configuration update is initiated, all out-of-band modifications will be overwritten during the rolling update.

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