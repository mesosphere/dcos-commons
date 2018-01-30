---
layout: layout.pug
navigationTitle:
excerpt:
title: Limitations
menuWeight: 100

---

## Configurations

The "disk" configuration value is denominated in MB. We recommend you set the configuration value `log_retention_bytes` to a value smaller than the indicated "disk" configuration. See the Configuring section for instructions for customizing these values.

## Security

### Kerberos

When Kerberos is enabled, the broker VIP is disabled as Kerberized clients will not be able to use it. This is because each Kafka broker uses a specific Kerberos principal and cannot accept connections from a single unified principal which the VIP would require.

### Toggling Kerberos

Kerberos authentication can be toggled (enabled / disabled), but this triggers a rolling restart of the cluster. Clients configured with the old security settings will lose connectivity during and after this process. It is recommended that backups are made and downtime is scheduled.

### Toggling TLS

Transport encryption using TLS can be toggled (enabled / disabled), but will trigger a rolling restart of the cluster. As each broker restarts, a client may lose connectivity based on its security settings and the value of the `service.security.transport_encryption.allow_plaintext` configuration option. It is recommended that backups are made and downtime is scheduled.

In order to enable TLS, a service account and corresponding secret is required. Since it is not possible to change the service account used by a service, it is recommended that the service is deployed with an explicit service account to allow for TLS to be enabled at a later stage.


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
