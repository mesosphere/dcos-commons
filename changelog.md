## Changes to v0.5x.y


## Changes to v0.55.4

- [DCOS-48617](https://jira.mesosphere.com/browse/DCOS-48617) Allow reorder of existing ports.
- [COPS-4469](https://jira.mesosphere.com/browse/COPS-4469) Shutdown the scheduler when service user changes in a ServiceSpec update.
- [DCOS-49331](https://jira.mesosphere.com/browse/DCOS-49331) Optimize how SDK writes to zk node to control the amount of curator transaction logs generated.
- [DCOS-48059](https://jira.mesosphere.com/browse/DCOS-48059) Fix a bug where duplicate StoreTaskInfoRecommendations are generated resulting in null values when querying the `v1/pod` endpoint.
- [DCOS-49350](https://jira.mesosphere.com/browse/DCOS-49350) Handle cases where SDK upgrade fails from 40 -> 50 when new tasks are added to the ServiceSpec.

#### Breaking changes:
- [DCOS-42593](https://jira.mesosphere.com/browse/DCOS-42593) Bump curator version to `4.0.1` and split up transactions using curator multi transactions. Frameworks should still be upgradable from previous 0.55.x to 0.55.4 without any changes.
