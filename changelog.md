## Changes to v0.5x.y

## Changes to v0.57.4-alpha

This is an *alpha* release to try out the upcoming Custom TLS artifacts feature.
As this is an alpha feature and release, changes can happen at any point in the future
and no support is provided.

The SDK now allows a TLS artifact to be referenced from the secret store via the following
`transport-encryption` Service-Spec changes.

### Mount-Path specified.
```
transport-encryption:
  - name: custom-secret
    secret: hello-world/custom-secret
    mount-path: ssl/custom-secret.pem
    type: CUSTOM
```
When `mount-path` is specified, the artifact will be surfaced in the Mesos sandbox with the value 
specified using `mount-path`.

### No Mount-Path specified.
```
transport-encryption:
  - name: custom-secret.pem
    secret: hello-world/custom-secret
    type: CUSTOM
```
When `mount-path` isn't specified, the artifact will be surfaced in the Mesos sandbox with the value
specified using `name`.

## Changes to v0.57.3

- [#3215](https://github.com/mesosphere/dcos-commons/pull/3215) Quota - Framework Uninstall Fixes.
- [#3209](https://github.com/mesosphere/dcos-commons/pull/3209) Fix for Jersey exceptions filling scheduler stderr logs.
- [#3219](https://github.com/mesosphere/dcos-commons/pull/3219) Check TaskId exists for auxiliary pod tasks before reset of Backoff.
- [#3206](https://github.com/mesosphere/dcos-commons/pull/3206) Add `test` as one of the roles to configure for strict mode clusters. [TOOLING]
- [#3210](https://github.com/mesosphere/dcos-commons/pull/3210) Misc fixes for MWT runs. [TOOLING]
- [#3217](https://github.com/mesosphere/dcos-commons/pull/3217) Fix deprecated warnings of PMD gradle plugin. [TOOLING]
- [#3216](https://github.com/mesosphere/dcos-commons/pull/3216) Fixed publish http server launch. [TOOLING]

##### Notes

- [#3215](https://github.com/mesosphere/dcos-commons/pull/3215) Is a major bug-fix since v0.57.0. Frameworks are recommended to upgrade to v0.57.3 and issue `pod replace` commands to exisiting deployments to mitigate the risks. Existing procedures for [migrating a existing service to the quotated role](https://github.com/mesosphere/dcos-commons/releases/tag/0.57.0#migrate-an-existing-deployed-service-to-use-quota-support) should be followed.

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

