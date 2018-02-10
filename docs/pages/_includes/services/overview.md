---
layout: layout.pug
navigationTitle: Overview
title:
menuWeight: 0
excerpt:
---

## Components

The following components work together to deploy and maintain the service.

- Mesos

  Mesos is the foundation of the DC/OS cluster. Everything launched within the cluster is allocated resources and managed by Mesos. A typical Mesos cluster has one or three Masters that manage resources for the entire cluster. On DC/OS, the machines running the Mesos Masters will typically run other cluster services as well, such as Marathon and Cosmos, as local system processes. Separately from the Master machines are the Agent machines, which are where in-cluster processes are run. For more information on Mesos architecture, see the [Apache Mesos documentation](https://mesos.apache.org/documentation/latest/architecture/). For more information on DC/OS architecture, see the [DC/OS architecture documentation](https://docs.mesosphere.com/1.9/overview/architecture/).

- ZooKeeper

  ZooKeeper is a common foundation for DC/OS system components, like Marathon and Mesos. It provides distributed key-value storage for configuration, synchronization, name registration, and cluster state storage. DC/OS comes with ZooKeeper installed by default, typically with one instance per DC/OS master.

  SDK Schedulers use the default ZooKeeper instance to store persistent state across restarts (under znodes named `dcos-service-<svcname>`). This allows Schedulers to be killed at any time and continue where they left off.

  **Note:** SDK Schedulers currently require ZooKeeper, but any persistent configuration storage (such as etcd) could fit this role. ZooKeeper is a convenient default because it is always present in DC/OS clusters.

- Marathon

  Marathon is the "init system" of a DC/OS cluster. Marathon launches tasks in the cluster and keeps them running. From the perspective of Mesos, Marathon is itself another Scheduler running its own tasks. Marathon is more general than SDK Schedulers and mainly focuses on tasks that don't require managing local persistent state. SDK services rely on Marathon to run the Scheduler and to provide it with a configuration via environment variables. The Scheduler, however, maintains its own service tasks without any direct involvement by Marathon.

- Scheduler

  The Scheduler is the "management layer" of the service. It launches the service nodes and keeps them running. It also exposes endpoints to allow end users to control the service and diagnose problems. The Scheduler is kept online by the cluster's "init system", Marathon. The Scheduler itself is effectively a Java application that is configured via environment variables provided by Marathon.

- Packaging

  {{ include.data.techName }} is packaged for deployment on DC/OS. DC/OS packages follow the [Universe schema](https://github.com/mesosphere/universe), which defines how packages expose customization options at initial installation. When a package is installed on the cluster, the packaging service (named 'Cosmos') creates a Marathon app that contains a rendered version of the `marathon.json.mustache` template provided by the package. For DC/OS {{ include.data.techName }}, this Marathon app is the scheduler for the service.

For further discussion of DC/OS components, see the [architecture documentation](https://docs.mesosphere.com/1.9/overview/architecture/components/).

## Deployment

Internally, {{ include.data.packageName }} treats "Deployment" as moving from one state to another state. By this definition, "Deployment" applies to many scenarios:

- When {{ include.data.packageName }} is first installed, deployment is moving from a null configuration to a deployed configuration.
- When the deployed configuration is changed by editing an environment variable in the scheduler, deployment is moving from an initial running configuration to a new proposed configuration.

In this section, we'll describe how these scenarios are handled by the scheduler.

### Initial Install

This is the flow for deploying a new service:

#### Steps handled by the DC/OS cluster

1. The user runs `dcos package install {{ include.data.packageName }}` in the DC/OS CLI or clicks `Install` for a given package on the DC/OS Dashboard.

1. A request is sent to the Cosmos packaging service to deploy the requested package along with a set of configuration options.

1. Cosmos creates a Marathon app definition by rendering {{ include.data.packageName }}'s `marathon.json.mustache` with the configuration options provided in the request, which represents {{ include.data.packageName }}'s Scheduler. Cosmos queries Marathon to create the app.

1. Marathon launches the {{ include.data.packageName }}'s scheduler somewhere in the cluster using the rendered app definition provided by Cosmos.

1. {{ include.data.packageName }}'s scheduler is launched. From this point onwards, the SDK handles deployment.

#### Steps handled by the Scheduler

{{ include.data.packageName }}'s `main()` function is run like any other Java application. The scheduler starts with the following state:

- A `svc.yml` template that represents the service configuration.
- Environment variables provided by Marathon, to be applied onto the `svc.yml` template.
- Any custom logic implemented by the service developer in their Main function (we'll be assuming this is left with defaults for the purposes of this explanation).

1. The `svc.yml` template is rendered using the environment variables provided by Marathon.

1. The rendered `svc.yml` "Service Spec" contains the host/port for the ZooKeeper instance, which the Scheduler uses for persistent configuration/state storage. The default is `master.mesos:2181`, but may be manually configured to use a different ZooKeeper instance. The Scheduler always stores its information under a znode named `dcos-service-<svcname>`.

1. The Scheduler connects to that ZooKeeper instance and checks to see if it has previously stored a Mesos Framework ID for itself.

  - If the Framework ID is present, the Scheduler will attempt to reconnect to Mesos using that ID. This may result in a "[Framework has been removed](#framework-has-been-removed)" error if Mesos doesn't recognize that Framework ID, indicating an incomplete uninstall.

  - If the Framework ID is not present, the Scheduler will attempt to register with Mesos as a Framework. Assuming this is successful, the resulting Framework ID is then immediately stored.

1. Now that the Scheduler has registered as a Mesos Framework, it is able to start interacting with Mesos and receiving offers. When this begins, the scheduler will begin running the [Offer Cycle](#offer-cycle) and deploying {{ include.data.packageName }}. See that section for more information.

1. The Scheduler retrieves its deployed task state from ZooKeeper and finds that there are tasks that should be launched. This is the first launch, so all tasks need to be launched.

1. The Scheduler deploys those missing tasks through the Mesos offer cycle using a [Deployment Plan](#plans) to determine the ordering of that deployment.

1. Once the Scheduler has launched the missing tasks, its current configuration should match the desired configuration defined by the "Service Spec" extracted from `svc.yml`.

    1. When the current configuration matches the desired configuration, the Scheduler will tell Mesos to suspend sending new offers, as there's nothing to be done.
    1. The Scheduler idles until it receives an RPC from Mesos notifying it of a task status change, it receives an RPC from an end user against one of its HTTP APIs, or until it is killed by Marathon as the result of a configuration change.

### Reconfiguration

This is the flow for reconfiguring a DC/OS service either in order to update specific configuration values, or to upgrade it to a new package version.

#### Steps handled by the DC/OS cluster

1. The user edits the Scheduler's environment variables either using the Scheduler CLI's `update` command or via the DC/OS GUI.
1. The DC/OS package manager instructs Marathon to kill the current Scheduler and launch a new Scheduler with the updated environment variables.

#### Steps handled by the Scheduler

As with initial install above, at this point the Scheduler is re-launched with the same three sources of information it had before:
- `svc.yml` template.
- New environment variables.
- Custom logic implemented by the service developer (if any).

In addition, the Scheduler now has a fourth piece:
- Preexisting state in ZooKeeper

Scheduler reconfiguration is slightly different from initial deployment because the Scheduler is now comparing its current state to a non-empty prior state and determining what needs to be changed.

1. After the Scheduler has rendered its `svc.yml` against the new environment variables, it has two Service Specs, reflecting two different configurations.
    1. The Service Spec that was just rendered, reflecting the configuration change.
    1. The prior Service Spec (or "Target Configuration") that was previously stored in ZooKeeper.
1. The Scheduler automatically compares the changes between the old and new Service Specs.
    1. __Change validation__: Certain changes, such as editing volumes and scale-down, are not currently supported because they are complicated and dangerous to get wrong.
        - If an invalid change is detected, the Scheduler will send an error message and refuse to proceed until the user has reverted the change by relaunching the Scheduler app in Marathon with the prior config.
        - If the changes are valid, the new configuration is stored in ZooKeeper as the new Target Configuration and the change deployment proceeds as described below.
    1. __Change deployment__: The Scheduler produces a `diff` between the current state and some future state, including all of the Mesos calls (reserve, unreserve, launch, destroy, etc.) needed to get there. For example, if the number of tasks has been increased, then the Scheduler will launch the correct number of new tasks. If a task configuration setting has been changed, the Scheduler will deploy that change to the relevant affected tasks by relaunching them. Tasks that aren't affected by the configuration change will be left as-is.
    1. __Custom update logic__: Some services may have defined a [custom `update` Plan](#custom-update-plan) in its `svc.yml`, in cases where different logic is needed for an update/upgrade than is needed for the initial deployment. When a custom `update` plan is defined, the Scheduler will automatically use this Plan, instead of the default `deploy` Plan, when rolling out an update to the service.

### Uninstall

This is the flow for uninstalling {{ include.data.packageName }}.

#### Steps handled by the cluster

1. The user uses the DC/OS CLI's `dcos package uninstall` command to uninstall the service.
1. The DC/OS package manager instructs Marathon to kill the current Scheduler and to launch a new Scheduler with the environment variable `SDK_UNINSTALL` set to "true".

#### Steps handled by the Scheduler

When started in uninstall mode, the Scheduler performs the following actions:
- Any Mesos resource reservations are unreserved.
  - **Warning**: Any data stored in reserved disk resources will be irretrievably lost.
- Preexisting state in ZooKeeper is deleted.

## Offer Cycle

The Offer Cycle is a core Mesos concept and often a source of confusion when running services on Mesos.

Mesos will periodically notify subscribed Schedulers of resources in the cluster. Schedulers are expected to either accept the offered resources or decline them. In this structure, Schedulers never have a complete picture of the cluster, they only know about what's being explicitly offered to them at any given time. This allows Mesos the option of only advertising certain resources to specific Schedulers, without requiring any changes on the Scheduler's end, but it also means that the Scheduler cannot deterministically know whether it's seen everything that's available in the cluster.

{{ include.data.packageName }} performs the following operations as Offers are received from Mesos:

1. __Task Reconciliation__: Mesos is the source of truth for what is running on the cluster. Task Reconciliation allows Mesos to convey the status of all tasks being managed by the service. The Scheduler will request a Task Reconciliation during initial startup, and Mesos will then send the current status of that Scheduler's tasks. This allows the Scheduler to catch up with any potential status changes to its tasks that occurred after the Scheduler was last running. A common pattern in Mesos is to jealously guard most of what it knows about tasks, so this only contains status information, not general task information. The Scheduler keeps its own copy of what it knows about tasks in ZooKeeper. During an initial deployment this process is very fast as no tasks have been launched yet.
1. __Offer Acceptance__: Once the Scheduler has finished Task Reconciliation, it will start evaluating the resource offers it receives to determine if any match the requirements of the next task(s) to be launched. At this point, users on small clusters may find that the Scheduler isn't launching tasks. This is generally because the Scheduler isn't able to find offered machines with enough room to fit the tasks. To fix this, add more/bigger machines to the cluster, or reduce the requirements of the service.
1. __Resource Cleanup__: The Offers provided by Mesos include reservation information if those resources were previously reserved by the Scheduler. The Scheduler will automatically request that any unrecognized but reserved resources be automatically unreserved. This can come up in a few situations, for example, if an agent machine went away for several days and then came back, its resources may still be considered reserved by Mesos as reserved by the service, while the Scheduler has already moved on and doesn't know about it anymore. At this point, the Scheduler will automatically clean up those resources.

{{ include.data.packageName }} will automatically notify Mesos to stop sending offers, or "suspend" offers, when the Scheduler doesn't have any work to do. For example, once a service deployment has completed, the Scheduler will request that offers be suspended. If the Scheduler is later notified that a task has exited via a status update, the Scheduler will resume offers in order to redeploy that task back where it was. This is done by waiting for the offer that matches that task's reservation, and then launching the task against those resources once more.

## Pods

A Task generally maps to a single process within the service. A Pod is a collection of colocated Tasks that share an environment. All Tasks in a Pod will come up and go down together. Therefore, most maintenance operations against the service are at [Pod granularity](#pod-operations) rather than Task granularity.

## Plans

The Scheduler organizes its work into a list of Plans. Every SDK Scheduler has at least a Deployment Plan and a [Recovery Plan](#recovery-plan), but other Plans may also be added for things like custom Backup and Restore operations. The Deployment Plan is in charge of performing an initial deployment of the service. It is also used for rolling out configuration changes to the service (or in more abstract terms, handling the transition needed to get the service from some state to another state), unless the service developer provided a [custom `update` Plan](#custom-update-plan). The Recovery Plan is in charge of relaunching any exited tasks that should always be running.

Plans have a fixed three-level hierarchy. Plans contain Phases, and Phases contain Steps.

For example, let's imagine a service with two `index` nodes and three `data` nodes. The Plan structure for a Scheduler in this configuration could look like this:

- Deployment Plan (`deploy`)
    - Index Node Phase
        - Index Node 0 Step
        - Index Node 1 Step
    - Data Node Phase
        - Data Node 0 Step
        - Data Node 1 Step
        - Data Node 2 Step
- Custom Update Plan (`update`)
    - _(custom logic, if any, for rolling out a config update or software upgrade)_
- Recovery Plan (`recovery`)
    - _(phases and steps are autogenerated as failures occur)_
- Index Backup Plan
    - Run Reindex Phase
        - Index Node 0 Step
        - Index Node 1 Step
    - Upload Data Phase
        - Index Node 0 Step
        - Index Node 1 Step
- Data Backup Plan
    - Data Backup Phase
        - Data Node 0 Step
        - Data Node 1 Step
        - Data Node 2 Step

As you can see, in addition to the default Deployment and Recovery Plans, this Scheduler also has a custom Update Plan which provides custom logic for rolling out a change to the service. If a custom plan is not defined then the Deployment Plan is used for this scenario. In addition, the service defines auxiliary Plans that support other custom behavior, specifically one Plan that handles backing up Index nodes, and another for that backs up Data nodes. In practice, there would likely also be Plans for restoring these backups. These auxiliary Plans could all be invoked manually by an operator, and may include additional parameters such as credentials or a backup location. Those are omitted here for brevity.

In short, Plans are the SDK's abstraction for a sequence of tasks to be performed by the Scheduler. By default, these include deploying and maintaining the cluster, but additional maintenance operations may also be fit into this structure.

### Custom Update Plan

By default, the service will use the Deployment Plan when rolling out a configuration change or software upgrade, but some services may need custom logic in this scenario, in which case the service developer may have defined a custom plan named `update`.

### Recovery Plan

The other default Plan is the Recovery Plan, which handles bringing back failed tasks. The Recovery Plan listens for offers that can be used to bring back those tasks and then relaunches tasks against those offers.

The Scheduler learns whether a task has failed by receiving Task Status updates from Mesos. Task Status updates can be sent during startup to let the scheduler know when a task has started running, to know when the task has exited successfully, or to know when the cluster has lost contact with the machine hosting that task.

When it receives a Task Status update, the Scheduler decides whether a given update indicates a task that needs to be relaunched. When a task must be relaunched, the Scheduler will wait on the Offer cycle.

#### Permanent and temporary recovery

There are two types of recovery, permanent and temporary. The difference is mainly whether the task being recovered should stay on the same machine, and the side effects that result from that.

- __Temporary__ recovery:
    - Temporary recovery is triggered when there is a hiccup in the task or the host machine.
    - Recovery involves relaunching the task on the same machine as before.
    - Recovery occurs automatically.
    - Any data in the task's persistent volumes survives the outage.
    - May be manually triggered by a `pod restart` command.
- __Permanent__ recovery:
    - Permanent recovery can be requested when the host machine fails permanently or when the host machine is scheduled for downtime.
    - Recovery involves discarding any persistent volumes that the pod once had on the host machine.
    - Recovery only occurs in response to a manual `pod replace` command (or operators may build their own tooling to invoke the replace command).

Triggering a permanent recovery is a destructive operation, as it discards any prior persistent volumes for the pod being recovered. This is desirable when the operator knows that the previous machine isn't coming back. For safety's sake, permanent recovery is currently not automatically triggered by the SDK itself.

## Persistent Volumes

The SDK was created to help simplify the complexity of dealing with persistent volumes. SDK services currently treat volumes as tied to specific agent machines, as one might have in a datacenter with local drives in each system. While EBS or SAN volumes, for instance, can be re-mounted and reused across machines, this isn't yet supported in the SDK.

Volumes are advertised as resources by Mesos, and Mesos offers multiple types of persistent volumes. The SDK supports two of these types: MOUNT volumes and ROOT volumes.

- __ROOT__ volumes:
    - Use a shared filesystem tree.
    - Share I/O with anything else on that filesystem.
    - Are supported by default in new deployments and do not require additional cluster-level configuration.
    - Are allocated exactly the amount of disk space that was requested.
- __MOUNT__ volumes:
    - Use a dedicated partition.
    - Have dedicated I/O for the partition.
    - Require [additional configuration](https://docs.mesosphere.com/1.9/storage/mount-disk-resources/) when setting up the DC/OS cluster.
    - Are allocated the entire partition, so allocated space can far exceed what was originally requested. MOUNT volumes cannot be further subdivided between services.

The fact that MOUNT volumes cannot be subdivided between services means that if multiple services are deployed with MOUNT volumes, they can quickly be unable to densely colocate within the cluster unless many MOUNT volumes are created on each agent. Let's look at the following deployment scenario across three DC/OS agent machines, each with two enabled MOUNT volumes labeled A and B:

```
Agent 1: A B
Agent 2: A B
Agent 3: A B
```

Now we install a service X with two nodes that each use one mount volume. The service consumes volume A on agents 1 and 3:

```
Agent 1: X B
Agent 2: A B
Agent 3: X B
```

Now a service Y is installed with two nodes that each use two mount volumes. The service consumes volume A and B on agent 2, but then is stuck without being able to deploy anything else:

```
Agent 1: X B
Agent 2: Y Y
Agent 3: X B
```

Configuring `ROOT` vs `MOUNT` volumes may depend on the service. Some services will support customizing this setting when it is relevant, while others may assume one or the other.

## Virtual networks

The SDK allows pods to join virtual networks, with the `dcos` virtual network available by defualt. You can specify that a pod should join the virtual network by using the `networks` keyword in your YAML definition. Refer to the [Developer Guide](../developer-guide/) for more information about how to define virtual networks in your service.

When a pod is on a virtual network such as the `dcos`:
  * Every pod gets its own IP address and its own array of ports.
  * Pods do not use the ports on the host machine.
  * Pod IP addresses can be resolved with the DNS: `<task_name>.<service_name>.autoip.dcos.thisdcos.directory`.
  * You can also pass labels while invoking CNI plugins. Refer to the [Developer Guide](../developer-guide/) for more information about adding CNI labels.

## Secrets

Enterprise DC/OS provides a secrets store to enable access to sensitive data such as database passwords, private keys, and API tokens. DC/OS manages secure transportation of secret data, access control and authorization, and secure storage of secret content.

The content of a secret is copied and made available within the pod. The SDK allows secrets to be exposed to pods as a file and/or as an environment variable. Refer to the [Developer Guide](../developer-guide/) for more information about how DC/OS secrets are integrated into SDK-based services. If the content of the secret is changed, the relevant pod needs to be restarted so that it can get updated content from the secret store.

**Note:** Secrets are available only in Enterprise DC/OS 1.10 onwards. [Learn more about the secrets store](https://docs.mesosphere.com/1.10/security/secrets/).


### Authorization for Secrets

The path of a secret defines which service IDs can have access to it. You can think of secret paths as namespaces. _Only_ services that are under the same namespace can read the content of the secret.


| Secret                               | Service ID                          | Can service access secret? |
|--------------------------------------|-------------------------------------|----------------------------|
| `Secret_Path1`		       | `/user`         		     | Yes   			  |
| `Secret_Path1`		       | `/dev1/user`         		     | Yes   			  |
| `secret-svc/Secret_Path1`            | `/user`                             | No                         |
| `secret-svc/Secret_Path1`            | `/user/dev1`                        | No                         |
| `secret-svc/Secret_Path1`            | `/secret-svc`                       | Yes                        |
| `secret-svc/Secret_Path1`            | `/secret-svc/dev1`                  | Yes                        |
| `secret-svc/Secret_Path1`            | `/secret-svc/instance2/dev2`        | Yes                        |
| `secret-svc/Secret_Path1`            | `/secret-svc/a/b/c/dev3`            | Yes                        |
| `secret-svc/instance1/Secret_Path2`  | `/secret-svc/dev1`                  | No                         |
| `secret-svc/instance1/Secret_Path2`  | `/secret-svc/instance2/dev3`        | No                         |
| `secret-svc/instance1/Secret_Path2`  | `/secret-svc/instance1`             | Yes                        |
| `secret-svc/instance1/Secret_Path2`  | `/secret-svc/instance1/dev3`        | Yes                        |
| `secret-svc/instance1/Secret_Path2`  | `/secret-svc/instance1/someDir/dev3`| Yes                        |



**Note:** Absolute paths (paths with a leading slash) to secrets are not supported. The file path for a secret must be relative to the sandbox.

### Binary Secrets

You can store binary files, like a Kerberos keytab, in the DC/OS secrets store. In DC/OS 1.11+ you can create secrets from binary files directly, while in DC/OS 1.10 or lower, files must be base64-encoded as specified in RFC 4648 prior to being stored as secrets.

#### DC/OS 1.11+

To create a secret called `mysecret` with the binary contents of `kerb5.keytab` run:

```bash
$ dcos security secrets create --file kerb5.keytab mysecret
```

#### DC/OS 1.10 or lower

To create a secret called `mysecret` with the binary contents of `kerb5.keytab`, first encode it using the `base64` command line utility. The following example uses BSD `base64` (default on macOS).

```bash
$ base64 --input krb5.keytab > kerb5.keytab.base64-encoded
```

Alternatively, GNU `base64` (the default on Linux) inserts line-feeds in the encoded data by default. Disable line-wrapping with the `-w 0` argument.

```bash
$ base64 -w 0 krb5.keytab > kerb5.keytab.base64-encoded
```

Now that the file is encoded it can be stored as a secret.

```bash
$ dcos security secrets create --value-file kerb5.keytab.base64-encoded some/path/__dcos_base64__mysecret
```

**Note:** The secret name **must** be prefixed with `__dcos_base64__`.

When the `some/path/__dcos_base64__mysecret` secret is [referenced in your service definition](../developer-guide.md#secrets), its base64-decoded contents will be made available as a [temporary file](http://mesos.apache.org/documentation/latest/secrets/#file-based-secrets) in your service task containers. **Note:** Make sure to only refer to binary secrets as files since holding binary content in environment variables is discouraged.

## Placement Constraints

Placement constraints allow you to customize where a service is deployed in the DC/OS cluster. Depending on the service, some or all components may be configurable using [Marathon operators (reference)](http://mesosphere.github.io/marathon/docs/constraints.html) with this syntax: `field:OPERATOR[:parameter]`. For example, if the reference lists `[["hostname", "UNIQUE"]]`, you should  use `hostname:UNIQUE`.

A common task is to specify a list of whitelisted systems to deploy to. To achieve this, use the following syntax for the placement constraint:
```
hostname:LIKE:10.0.0.159|10.0.1.202|10.0.3.3
```

You must include spare capacity in this list, so that if one of the whitelisted systems goes down, there is still enough room to repair your service (via [`pod replace`](#replace-a-pod)) without requiring that system.

### Regions and Zones

Placement constraints can be applied to zones by referring to the `@zone` key. For example, one could spread pods across a minimum of 3 different zones by specifying the constraint:
```
[["@zone", "GROUP_BY", "3"]]
```

When the region awareness feature is enabled (currently in beta), the `@region` key can also be referenced for defining placement constraints. Any placement constraints that do not reference the `@region` key are constrained to the local region.

#### Rack aware services

Many rack aware services do not support reliable migration between rack-aware and non-rack-aware operation.  These services can choose to restrict the set of placement constraint transitions which are valid through use of the `ZoneValidator`.  The allowed placement constraint transitions when using the `ZoneValidator` are as follows:

##### Placement Constraint References Zones

| Original Constraint | New Constraint |
|---------------------|----------------|
| None                | False          |
| False               | False          |
| True                | True           |
|                     |                |

### Updating placement constraints

Clusters change, and as such so should your placement constraints. We recommend using the following procedure to do this:
- Update the placement constraint definition at the Scheduler.
- For each pod, _one at a time_, perform a `pod replace` for any pods that need to be moved to reflect the change.

For example, let's say we have the following deployment of our imaginary `data` nodes, with manual IPs defined for placing the nodes in the cluster:

- Placement constraint of: `hostname:LIKE:10.0.10.3|10.0.10.8|10.0.10.26|10.0.10.28|10.0.10.84`
- Tasks:
```
10.0.10.3: data-0
10.0.10.8: data-1
10.0.10.26: data-2
10.0.10.28: [empty]
10.0.10.84: [empty]
```

Given the above configuration, let's assume `10.0.10.8` is being decommissioned and our service should be moved off of it. Steps:

1. Remove the decommissioned IP and add a new IP to the placement rule whitelist, by configuring the Scheduler environment with a new `DATA_NODE_PLACEMENT` setting:
   ```
   hostname:LIKE:10.0.10.3|10.0.10.26|10.0.10.28|10.0.10.84|10.0.10.123
   ```
1. Wait for the Scheduler to restart with the new placement constraint setting.
1. Trigger a redeployment of `data-1` from the decommissioned node to a new machine within the new whitelist: `dcos myservice node replace data-1`
1. Wait for `data-1` to be up and healthy before continuing with any other replacement operations.

The ability to configure placement constraints is defined on a per-service basis. Some services may offer very granular settings, while others may not offer them at all. You'll need to consult the documentation for the service in question, but in theory they should all understand the same set of [Marathon operators](http://mesosphere.github.io/marathon/docs/constraints.html).

## Integration with DC/OS access controls

In DC/OS 1.10 and above, you can integrate your SDK-based service with DC/OS ACLs to grant users and groups access to only certain services. You do this by installing your service into a folder, and then restricting access to some number of folders. Folders also allow you to namespace services. For instance, `staging/kafka` and `production/kafka`.

Steps:

1. In the DC/OS GUI, create a group, then add a user to the group. Or, just create a user. Click **Organization** > **Groups** > **+** or **Organization** > **Users** > **+**. If you create a group, you must also create a user and add them to the group.
1. Give the user permissions for the folder where you will install your service. In this example, we are creating a user called `developer`, who will have access to the `/testing` folder.
   Select the group or user you created. Select **ADD PERMISSION** and then toggle to **INSERT PERMISSION STRING**. Add each of the following permissions to your user or group, and then click **ADD PERMISSIONS**.

   ```
   dcos:adminrouter:service:marathon full
   dcos:service:marathon:marathon:services:/testing full
   dcos:adminrouter:ops:mesos full
   dcos:adminrouter:ops:slave full
   ```
1. Install a service (in this example, Kafka) into a folder called `test`. Go to **Catalog**, then search for **beta-kafka**.
1. Click **CONFIGURE** and change the service name to `/testing/kafka`, then deploy.

   The slashes in your service name are interpreted as folders. You are deploying Kafka in the `/testing` folder. Any user with access to the `/testing` folder will have access to the service.

**Important:**
- Services cannot be renamed. Because the location of the service is specified in the name, you cannot move services between folders.
- DC/OS 1.9 and earlier does not accept slashes in service names. You may be able to create the service, but you will encounter unexpected problems.

### Interacting with your foldered service

- Interact with your foldered service via the DC/OS CLI with this flag: `--name=/path/to/myservice`.
- To interact with your foldered service over the web directly, use `http://<dcos-url>/service/path/to/myservice`. E.g., `http://<dcos-url>/service/testing/kafka/v1/endpoints`.
