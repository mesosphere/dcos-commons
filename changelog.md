## Changes to v0.58.0-rc4
- Converts NetApp driver to Generic Driver (no driver name needed ``).
- Adds test harness support for external volumes.

## Changes to v0.58.0-rc3
- Removes option of passing arbitrary driver name for external volume plugin
- Add support for Potworx (driver name `pxd`) as well as NetApp (driver name `netapp`) driver plugin

## Changes to v0.58.0-rc2
- Add pod type to default externl volume name in addition to pod index
- Add configurable Replacement Failure Policy support

## Changes to v0.58.0-rc1

- [D2IQ-62959](https://jira.d2iq.com/browse/D2IQ-62959) Add support for resource-limits in SDK
- [D2IQ-70241](https://jira.d2iq.com/browse/D2IQ-70241), [D2IQ-70242](https://jira.d2iq.com/browse/D2IQ-70242) Add beta support for DVDI in SDK

### Support external volumes via the [Docker Volume Isolator](http://mesos.apache.org/documentation/latest/isolators/docker-volume/)

DVDI support allow external volumes to be mounted in data services built on the SDK. Only Portworx volume driver is supported.

### Resource limits (AKA vertical bursting)

The resource limits change brings several important changes to the SDK in how it launches tasks. In order to enable smooth upgrades, it is critical that every data service built on the SDK be adapted with the changes and potential problems in to account.

#### Tasks no longer share resources with other tasks

Previously, sidecar tasks (such as running Cassandra `nodetool repair`) were able to consume memory and cpu resources from the primary task. This is because Mesos previously launched all tasks in to a single cgroups. SDK v0.58.0 will instruct Mesos to launch all tasks in separate cgroups. This means that if a sidecar task actually needs more memory than that which it specifically requests, and no configuration is changed, the sidecar task **will get OOM killed**.

To remedy this, all frameworks should update their service specs so that ultimately the resource-limits for both the primary data service task, and side-car tasks, can be defined. Further, the templates in the Universe should expose appropriate configuration parameters so that bursting for both can be defined.

#### Tasks can be configured to optional consume more than they request.

With resource-limits, a task can be configured to consume more CPU or Memory than that which is requested and reserved. This is fantastic news for data-services that would permanently set aside an entire CPU so that occassional backup or repair side-car tasks can be run. To repeat a point made before, SDK service templates should expose appropriate configuration parameters so that resource-limits can be set, at the very least, for sidecar tasks.

Instead of Cassandra requiring 1 CPU for a sidecar tasks, it could instead set aside `0.1` CPU, and then set a resource limit of up to 2 CPUs. This will allow the task to run fast when their are leftover resources to run them, and leave CPUs available to service time-sensitive API requests.

## Changes to v0.57.3

- [#3215](https://github.com/mesosphere/dcos-commons/pull/3215) Quota - Framework Uninstall Fixes.
- [#3209](https://github.com/mesosphere/dcos-commons/pull/3209) Fix for Jersey exceptions filling scheduler stderr logs.
- [#3219](https://github.com/mesosphere/dcos-commons/pull/3219) Check TaskId exists for auxiliary pod tasks before reset of Backoff.
- [#3206](https://github.com/mesosphere/dcos-commons/pull/3206) Add `test` as one of the roles to configure for strict mode clusters. [TOOLING]
- [#3210](https://github.com/mesosphere/dcos-commons/pull/3210) Misc fixes for MWT runs. [TOOLING]
- [#3217](https://github.com/mesosphere/dcos-commons/pull/3217) Fix deprecated warnings of PMD gradle plugin. [TOOLING]
- [#3216](https://github.com/mesosphere/dcos-commons/pull/3216) Fixed publish http server launch. [TOOLING]


## Changes to v0.57.2
- [#3198](https://github.com/mesosphere/dcos-commons/pull/3198) `ALLOW_REGION_AWARENESS` is set to true by default

## Changes to v0.57.1

- [#3184](https://github.com/mesosphere/dcos-commons/pull/3184) : Fix a bug where SDK was parsing empty fields in `env` field of Task definition in service yaml as `null` instead of blank strings.
- [#3185](https://github.com/mesosphere/dcos-commons/pull/3185) : Bump scheduler java runtime to JDK11 :rocket:

##### Notes
- In this version, the framework java runtime is unchanged but the scheduler runtime is bumped to Java 11. Scheduler may behave unexpectedly if Java 8 runtime is provided.

## Changes to v0.57.0

- [DCOS-55542](https://jira.mesosphere.com/browse/DCOS-55542) SDK Shared Memory Support (#3132)
- [DCOS-54278](https://jira.mesosphere.com/browse/DCOS-54278) SDK Quota Support (#3102)
- [#3176](https://github.com/mesosphere/dcos-commons/pull/3176) Remove Launch Constrainer
- [#3177](https://github.com/mesosphere/dcos-commons/pull/3177) Remove Manual Plan Sync

##### Notes
- Requires Mesos 1.9.0 and libmesos-bundle 1.14-beta

## Changes to v0.56.3
- [COPS-5286](https://jira.mesosphere.com/browse/COPS-5286) Allow use of Seccomp in DC/OS 1.12 (#3163)
- [COPS-5211](https://jira.mesosphere.com/browse/COPS-5211) Fix marathon constraint parser bug (#3160)

## Changes to v0.56.2

- [#3144](https://github.com/mesosphere/dcos-commons/pull/3144) : Added user level configuration support for specifying the host volume mode to one of `RW` or `RO`.
- [DCOS-54275](https://jira.mesosphere.com/browse/DCOS-54275) #3120 : Added support for auto task back off on task failures/errors.
- [#3070](https://github.com/mesosphere/dcos-commons/pull/3070) and [#3105](https://github.com/mesosphere/dcos-commons/pull/3105) : Add a config validator for seccomp.
- [DCOS-42593](https://jira.mesosphere.com/browse/DCOS-42593) The zookeeper max payload batching bug has finally been fixed via [#3147](https://github.com/mesosphere/dcos-commons/pull/3147)
- [#3060](https://github.com/mesosphere/dcos-commons/pull/3060) : Use Open JDK 8

##### Notes
- Currently, task backoff is disabled by default but this behaviour will be changed so that task back off is enabled by default in future major releases of SDK.
- The OpenJDK binaries used in this release (and future releases) have JRE at a different location. Refer to [#3060](https://github.com/mesosphere/dcos-commons/pull/3060) to see what changes are needed for a framework to adopt to this new file structure.

## Changes to v0.56.1

- [DCOS-53415](https://jira.mesosphere.com/browse/DCOS-53415) Update to mesos 1.8.0 `org.apache.mesos:mesos:1.8.0`
- [DCOS-OSS-5147](https://jira.mesosphere.com/browse/DCOS_OSS-5147) Handle duplicate pre-reserved roles across pods.

## Changes to v0.56.0

- [DCOS-49197](https://jira.mesosphere.com/browse/DCOS-49197) Introduce new service status codes.
- [DCOS-48777](https://jira.mesosphere.com/browse/DCOS-48777) The `init` developer helper script in docker image was renamed to `copy-files`
  
##### Notes
- The newly added status codes may not work if frameworks are using marathon health checks ([Deprecated since marathon 1.4.x](https://github.com/mesosphere/marathon/releases/tag/v1.4.0)). Please use MESOS Health checks [in your marathon.json.mustache](https://github.com/mesosphere/dcos-commons/blob/0.56.0/frameworks/helloworld/universe/marathon.json.mustache#L136-L146)

## Changes to v0.55.5

- [DCOS-51019](https://jira.mesosphere.com/browse/DCOS-51019) Support passing seccomp-undefined and seccomp-profile-name in svc yml.

##### Notes
- This version of SDK uses the mesos 1.8.0 `org.apache.mesos:mesos:1.8.0-SNAPSHOT` artifact located in `https://repository.apache.org/content/repositories/snapshots/` maven repo. Please update your build file to add this repository.

## Changes to v0.55.4

- [DCOS-48617](https://jira.mesosphere.com/browse/DCOS-48617) Allow reorder of existing ports.
- [COPS-4469](https://jira.mesosphere.com/browse/COPS-4469) Shutdown the scheduler when service user changes in a ServiceSpec update.
- [DCOS-49331](https://jira.mesosphere.com/browse/DCOS-49331) Optimize how SDK writes to zk node to control the amount of curator transaction logs generated.
- [DCOS-48059](https://jira.mesosphere.com/browse/DCOS-48059) Fix a bug where duplicate StoreTaskInfoRecommendations are generated resulting in null values when querying the `v1/pod` endpoint.
- [DCOS-49350](https://jira.mesosphere.com/browse/DCOS-49350) Handle cases where SDK upgrade fails from 40 -> 50 when new tasks are added to the ServiceSpec.

#### Breaking changes:
- [DCOS-42593](https://jira.mesosphere.com/browse/DCOS-42593) Bump curator version to `4.0.1` and split up transactions using curator multi transactions. Frameworks should still be upgradable from previous 0.55.x to 0.55.4 without any changes.

## Changes to v0.55.3

- [DCOS-48617](https://jira.mesosphere.com/browse/DCOS-48617) Dont evaluate existing reservations against new ports.

## Changes to v0.55.2

- [DCOS-40703](https://jira.mesosphere.com/browse/DCOS-40703) Automate pod replacement when a node is decommissioned.
- [DCOS-47365](https://jira.mesosphere.com/browse/DCOS-47365) Allow passing range constraints when requesting a dynamic port.

## Changes to v0.55.1

- [DCOS-46657](https://jira.mesosphere.com/browse/DCOS-46657) Allow tasks to request dynamic ports in pre-reserved roles.
- [COPS-4320](https://jira.mesosphere.com/browse/COPS-4320) Configureable RLIMITs for RLIMIT_NOFILE.

## Changes to v0.55.0

- [#2672](https://github.com/mesosphere/dcos-commons/pull/2672) Add support for Arbitrary Task Labels to Service Spec YAML.
- [#2785](https://github.com/mesosphere/dcos-commons/pull/2785) Made pod-profile-mount-volume service yaml more configurable.
- [#2797](https://github.com/mesosphere/dcos-commons/pull/2797) Refine the 'pod-profile-mount-volume' service definition.
- [DCOS-44704](https://jira.mesosphere.com/browse/DCOS-44704) Implement OfferOutcome Debug Endpoint.
- [DCOS-44703](https://jira.mesosphere.com/browse/DCOS-44703) Implement TaskStatus Endpoint.
- [DCOS-44702](https://jira.mesosphere.com/browse/DCOS-44702) Implement Plans Debug Endpoint.
- [COPS-4320](https://jira.mesosphere.com/browse/COPS-4320) Configureable timeouts for readiness checks.

#### Repository changes:
- [DCOS-45209](https://jira.mesosphere.com/browse/DCOS-45209) Remove Kafka framework from the mono-repo.

