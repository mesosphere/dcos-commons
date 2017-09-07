---
post_title: Limitations
menu_order: 50
enterprise: 'no'
---

# Nodes

The maximum number of deployable nodes is constrained by the DC/OS cluster's resources. Each Elasticsearch node has specified required resources, so nodes may not be placed if the DC/OS cluster lacks the requisite resources.

# Upgrade and Configuration Rolling Updates Do Not Wait for Health

The `serial` deploy strategy does wait for the cluster to reach green before proceeding to the next node.

## Out-of-band configuration

Out-of-band configuration modifications are not supported. The service's core responsibility is to deploy and maintain an instances with a specified configuration. In order to do this, the service assumes that it has ownership of task configuration. If an end-user makes modifications to individual tasks through out-of-band configuration operations, the service will almost certainly override those modifications at a later time. For example:
- If a task crashes, it will be restarted with the configuration known to the scheduler, not one modified out-of-band.
- If a configuration update is initiated, all out-of-band modifications will be overwritten during the rolling update.

## Scaling in

To prevent accidental data loss, the service does not support reducing the number of nodes.

## Disk changes

To prevent accidental data loss from reallocation, the service does not support changing volume requirements after initial deployment.

## Automatic recovery

The service does not automatically replace existing nodes which have permanently failed. The operator (or tooling built by the operator) should invoke `pod replace <pod>` or the underlying HTTP command manually. However, temporary failures where the host machine hasn't permanently failed will be recovered automatically.

## Best-effort installation

If your cluster doesn't have enough resources to deploy the service as requested, the initial deployment will not complete until either those resources are available or until you reinstall the service with corrected resource requirements.

## Virtual networks

When the service is deployed on a virtual network, the service may not be switched to host networking without a full re-installation. The same is true for attempting to switch from host to virtual networking.
