## Changes to v0.5x.y

## Changes to v0.56.0

- [DCOS-49197](https://jira.mesosphere.com/browse/DCOS-49197) Introduce new service status codes.

## Changes to v0.55.5

- [DCOS-51019](https://jira.mesosphere.com/browse/DCOS-51019) Support passing seccomp-undefined and seccomp-profile-name in svc yml.

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

