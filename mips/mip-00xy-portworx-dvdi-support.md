# SDK Portworx DVDI - MIP 000X - 2020-07-20 (Draft)

Directly Responsible Individual: Kaiwalya Joshi

## Motivation
[Portworx currently maintains a fork of DC/OS Commons SDK](https://github.com/portworx/dcos-commons) from which they've built data-services which support external volumes via the [Docker Volume Isolator](http://mesos.apache.org/documentation/latest/isolators/docker-volume/). The Portworx fork has drifted from the upstream version and has lagged behind. As of this writing the Portworx fork is at SDK v0.40.x whereas upstream SDK is at v0.57.0. Between v0.4x -> v0.5x there were significant internal changes to the SDK along with support for Quota and other Mesos features. Customers wish to use external volumes provisioned by Portworx along with latest DC/OS features surfaced by newer versions of the SDK.
The goal of this MIP is to add support for external volumes to the lastest version of the SDK via the Docker Volume Isolator, with the immediate goal of supporting the Portworx Provider.

## Scope
- Adding external volume support via Docker Volume Isolator.
- Adding support for Portworx `PWX` provider.

## Out of Scope
- Porting and testing of Portworx Data-Service Packages to upstream Mesosphere DC/OS Commons SDK.
- Support for external volumes via CSI.

## Interface Detail

### Mesos Requirements
Going from bottom up, the requirements from Mesos to expose a Docker Volume to a `ContainerInfo` are as follows:
- Volume Type of `DOCKER_VOLUME`
- Volume Size
- Driver Name
- Volume Name
- Driver Options
- Container Path
- Access Mode one of `RW|RO`

The interface exposed by the SDK must surface at least the above options.

An example snippet using the V0 Java Mesos API to use Docker Volumes is as follows:
```java
containerInfo.addVolumes(
    Protos.Volume.newBuilder()
        .setSource(
            Protos.Volume.Source.newBuilder()
                .setDockerVolume(
                    Protos.Volume.Source.DockerVolume.newBuilder()
                        .setDriver(dockerVolume.getDriverName())
                        .setName(volumeName)
                        .setDriverOptions(driverOptions)
                        .build())
                .setType(Protos.Volume.Source.Type.DOCKER_VOLUME)
                .build())
        .setMode(Protos.Volume.Mode.RW)
        .setContainerPath(volume.getContainerPath()));
```

### Proposed External-Volume Service-Spec

To surface the above options to the user, we propose the following changes to the [SDK Service-Spec](https://mesosphere.github.io/dcos-commons/developer-guide/#introduction-to-dcos-service-definitions)

```yaml
external-volumes:
  external-volume-1:
    type: DOCKER
    driver: PWX
    size: <size in MB>
    container-path: <path in container>
    driver-options: <driver-options>
    volume-name: <volume-name> 
    volume-mode: RW | RO (optional, default RW)
    volume-sharing: POD_EXCLUSIVE (default) | POD_SHARED (driver dependant)
```

The following fields are required for `external-volumes`:
- `type` - The type of external-volume. Currently only `DOCKER` is supported.
- `driver` - The driver of the external-volume. Currently only `PWX` is supported. 
- `size` - Size of the external volume. The capability to grow or shrink this volume is delegated to the provider.
- `container-path` - Path where the external volume is surfaced in the launched container.

The remaining fields are required by the Docker Volume Isolator which is specified by the `DOCKER` type:
- `driver-options`: String of options to pass to the driver. This is opaque to the SDK.
- `volume-name`: Name of the volume exposed to the provider.
- `volume-mode`: *Optional* Whether volume is read-write or read-only. Defaults to read-write mode.
- `volume-sharing`: Determines how the volumes are shared across the service.
    - `POD_EXCLUSIVE`: Each pod creates an exclusive non-shared volume suffixed with its pod-index after `volume-name`. This the default mode.
    - `POD_SHARED`: All the pods share the volume specified by `volume-name`.

## Orchestration Semantics
- Mesos Reservations

    For the non-external `ROOT` and `MOUNT` volumes, Mesos offers these resources to SDK scheduler and is ultimately aware of how these volumes are allocated across the cluster. External-volumes are **not** part of the the offer stream and as such allocation and usage of external-volumes is opaque to Mesos. The SDK as such will **not** have any reservations and associated reservation-ids for the external-volumes created.

- Pods and Reservations

    The SDK scheduler creates reservations for cpu, memory, ports & disk required to launch the default executor and to run the specified tasks within a pod.  As such task launches on the SDK are "sticky" to the agent where the pod was launched even though external-volumes are not tied to the agents and no Mesos reservations are created for these external-volumes. 

- Pods and External-Volumes
    
    The SDK Scheduler with `ROOT` and `MOUNT` volumes maintains a non-shared pod instance of data, sharing of data between pods in this case isn't supported.

    The SDK introduces the `volume-sharing` option to the interface, with the following semantics:
    - `POD_EXCLUSIVE`: This is the default where each pod gets its own exclusive non-shared volume. The operator can expect a volume created for each pod with `volume-name` suffixed with the corresponding index.
    - `POD_SHARED`: All the pods share the volume specified by `volume-name`.

- Pod Replacement Policies

    With the non-external `ROOT` and `MOUNT` volumes, recreating a pod via a `pod-replace` can be an expensive operation depending on the amount of data to be rebuilt. With data-services using external-volumes for their primary data, rebuilding a pod isn't expected to incur as heavy a penalty. The SDK as such suggests the use of automatic pod-replacement policies with external-volumes.

    Frameworks should set [ReplacementFailurePolicy](https://github.com/mesosphere/dcos-commons/blob/master/sdk/scheduler/src/main/java/com/mesosphere/sdk/specification/ReplacementFailurePolicy.java#L75-L121) in their Service-Specs. `ReplacementFailurePolicy` uses the same underlying mechanism as `pod-replace`. **Note** that `ReplacementFailurePolicy` applies to the entire service across all pods.

## Failure Semantics

- Container Launch Failure

    Container launch failures can occur due to issues with attaching the external-volume to the executor. In the event of a container launch failure, Mesos sends a `TASK_FAILURE` event to the SDK scheduler, details of which are outlined under Task Failure.

- External-Volume Provider Failures

    A failure with the operation of the external-volume provider will result in Mesos sending a `TASK_FAILURE` event to the SDK scheduler, details of which are outlined under Task Failure.

- Task Failure

    Mesos sends a `TASK_FAILURE` event to the SDK scheduler whereby it will relaunch the task with the existing reservations on the same agent. Repeated launches with a resulting `TASK_FAILURE` will put the assocated Scheduler Plan into a [`DELAYED` state where it re-launches the task with exponential-backoff](https://github.com/mesosphere/dcos-commons/pull/3120)

    The scheduler will relocate the task to another agent with the semantics of `Pod Replace` outlined below if `ReplacementFailurePolicy` is defined.

- Pod Restart

    The scheduler receives the `pod  restart` command from the operator, the scheduler then kills existing tasks and re-launches them with existing reservations on the same agent.

- Pod Replace

    The scheduler receives the `pod  replace` command from the operator, the scheduler then kills existing tasks and unreserves all resources associated with the pod. Offer matching begins again and the pod is launched on the next available agent that offers the required resources.

- Node Decommission

    Mesos sends a `TASK_GONE_BY_OPERATOR` on an agent decommission, the scheduler handles this with similar semantics as a `Pod Replace` event outlined above. The Portworx Fork currently doesn't have the ability to handle this scenario and treats the the task as `TASK_LOST`, operator intervention is required at this point.

## Differences from Portworx Port

There are notable differences between the Portworx Fork and the current proposal.

### Interface Service-Spec Differences

The Portworx Fork has opted to go with the following interface for the Service-Spec:
```yaml
volume:
  path: <path in container>
  type: DOCKER
  docker_volume_driver: pxd
  docker_volume_name: <volume-name>
  docker_driver_options: <driver-options>
  size: <size in MB>
```

The critical difference between the Portworx Fork and the current proposal is that the Portworx fork has introduced the `DOCKER` type alongside `ROOT` and `MOUNT` types under `volume` Service-Spec.

The `volume` Service-Spec type is intended to be used with resoures that Mesos is aware of and are in the offer stream as is the case for `ROOT` and `MOUNT` volumes.
The current implementation architecture of the SDK makes it awkward to deal with external-volumes whoose resources aren't part of the offer stream from Mesos. In practice the Portworx Fork [short-circuits the volume related offer-evaluation](https://github.com/portworx/dcos-commons/blob/portworx_1.3.5-2.0.3/sdk/scheduler/src/main/java/com/mesosphere/sdk/offer/evaluate/VolumeEvaluationStage.java#L108-L112) related aspects.

The current implementation as opted for a different `Service-Spec` type of `external-volumes` which differentiates it from `volumes` and intentionally prevents any short-circuits in the volume offer-evaluation logic. This approach is inline with the [`host-volume` feature](https://github.com/mesosphere/dcos-commons/releases/tag/0.53.0) which is also not part of the Mesos offer stream.

### Migration Path

Aside from the introduction of the `provider` and `volume-mode` fields and nesting under `external-volumes` as opposed to `volumes` under the Service-Spec, the interface differences between the two are largely cosmetic. The transition between the Portworx fork to the upstream SDK isn't intended or expected to be too onerous on the the Framework maintainers.

### Framework Adoption

### External Volume Configuration

The following JSON configuration is suggested to surface external-volume related settings to the user.
```json
"external-volumes": {
    "description": "Options relating to use of external volumes.",
    "use-external-volumes": {
        "description": "Use external-volumes over volumes managed by Mesos.",
        "type": "boolean",
        "default": false
    },
    "volume_name": {
        "description": "Volume name",
        "type": "string",
        "default": "DefaultVolumeName"
    },
    "volume_driver_options": {
        "description": "Volume driver options",
        "type": "string",
        "default": ""
    },
    "volume_sharing": {
        "description": "Volume sharing options",
        "type": "string",
        "enum": [
            "POD_EXCLUSIVE",
            "POD_SHARED"
        ],
        "default": "POD_EXCLUSIVE"
    },
    "pod-replacement-failure-policy": {
        "description": "Options relating to automatic pod-replacement failure policies with external-volumes.",
        "enable-automatic-pod-replacement": {
            "description": "Determines whether pods should be replaced automatically on failure.",
            "type": "boolean",
            "default": false
        },
        "permanent-failure-timeout": {
            "description": "Default time to wait before declaring a pod as permanently failed in seconds.",
            "type": "integer",
            "default": 1200
        },
        "min-replace-delay": {
            "description": "Default time to wait between successive pod-replace operations in seconds.",
            "type": "integer",
            "default": 600
        }
    }
}
```

`use-external-volumes`: Determines whether external-volumes should be used over `ROOT` or `MOUNT` volumes governed by Mesos.
`volume_name`: Maps to `volume-name` in the Service-Spec interface.
`volume_driver_options`: Maps to `driver-options` in the Service-Spec interface.
`volume_sharing`: Maps to `volume-sharing` in the Service-Spec interface.

### Automatic-Pod Replacement

It is suggested to use an automatic pod-replacement failure policy with external-storage as rebuilding data associated with a pod isn't expected to be as expensive as compared to equivalent `ROOT` or `MOUNT` volumes. The `pod-replacement-failure-policy` option deal with these aspects and are **applied across the service** and not just a single pod type.

- `enable-automatic-pod-replacement`: Determines whether to enable automatic Pod-Replacement-Failure policy. Frameworks should set [ReplacementFailurePolicy](https://github.com/mesosphere/dcos-commons/blob/master/sdk/scheduler/src/main/java/com/mesosphere/sdk/specification/ReplacementFailurePolicy.java#L75-L121) in their Service-Specifications when `enable-automatic-pod-replacement` is set.
- `permanent-failure-timeout`: The default time to wait before declaring a pod as permanently failed in minutes, this option is passed on to `ReplacementFailurePolicy`.
- `min-replace-delay`: The default time to wait between successive pod-relace operations in minutes, this option is passed on to `ReplacementFailurePolicy`.

## Implementation Outline

JIRA Epic: [D2IQ-67845](https://jira.d2iq.com/browse/D2IQ-67845)

1. YAML Service Spec Changes
    - Implement `ExternalVolumes` interface.
    - Implement `DockerVolumes` interface.
    - Implement `PWX` driver implementation.
1. Propagate pod-index to `PodInfoBuilder`
1. Implement `PWX` driver specific validation and other logic.
1. Propagate `DOCKER_VOLUME` requirements to `ContainerInfo`
1. Add Capabilities requirement.
1. Add Integration tests with `PWX`
    - Installation of Portworx Volumes on CI cluster.
    - Run hello-world with external-volumes.
        - Exercise all the failure semantics outlined above.
    - Cleanup of Portworx Volumes
1. Documentation Updates
    - Add `external-volumes` to the SDK Developer Guide.

## Future Aspects

- Implementation of `REXRAY` Docker Volume driver.
