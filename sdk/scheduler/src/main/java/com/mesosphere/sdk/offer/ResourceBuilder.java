package com.mesosphere.sdk.offer;

import java.util.Optional;

import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.Protos.Volume;

import com.mesosphere.sdk.specification.ResourceSpec;

/**
 * Constructs Mesos {@link Resource} protobufs.
 */
public class ResourceBuilder {

    /** The name used for reserved network port resources. */
    public static final String PORTS_RESOURCE_TYPE = "ports";
    /** The name used for reserved storage/disk resources. */
    public static final String DISK_RESOURCE_TYPE = "disk";

    /** The "any role" wildcard resource role. */
    private static final String ANY_ROLE = "*";


    private final String role;
    private final Optional<String> principal;
    private final String resourceName;
    private Value value;
    private Optional<String> resourceId;

    private Optional<String> diskContainerPath;
    private Optional<String> diskPersistenceId;
    private Optional<DiskInfo.Source> diskMountInfo;

    public static ResourceBuilder fromSpec(ResourceSpec spec) {
        return new ResourceBuilder(spec.getRole(), Optional.of(spec.getPrincipal()), spec.getName(), spec.getValue());
    }

    public static ResourceBuilder fromExistingResource(Resource resource) {
        ResourceBuilder builder =
                new ResourceBuilder(resource.getRole(), getPrincipal(resource), resource.getName(), getValue(resource));
        Optional<String> resourceId = ResourceCollectUtils.getResourceId(resource);
        if (resourceId.isPresent()) {
            builder.setResourceId(resourceId.get());
        }
        if (resource.hasDisk()) {
            DiskInfo diskInfo = resource.getDisk();
            String containerPath = diskInfo.getVolume().getContainerPath();
            Optional<String> persistenceId = diskInfo.getPersistence().getId().equals("")
                    ? Optional.empty()
                    : Optional.of(diskInfo.getPersistence().getId());
            if (diskInfo.hasSource() && diskInfo.getSource().getType() == DiskInfo.Source.Type.MOUNT) {
                Optional<String> mountRoot = diskInfo.getSource().hasMount()
                        ? Optional.of(diskInfo.getSource().getMount().getRoot())
                        : Optional.empty();
                builder.setMountVolume(containerPath, persistenceId, mountRoot);
            } else {
                builder.setRootVolume(containerPath, persistenceId);
            }
        }
        return builder;
    }

    public static ResourceBuilder fromUnreservedValue(String resourceName, Value value) {
        return new ResourceBuilder(ANY_ROLE, Optional.empty(), resourceName, value);
    }

    private ResourceBuilder(String role, Optional<String> principal, String resourceName, Value value) {
        this.role = role;
        this.principal = principal;
        this.resourceName = resourceName;
        this.value = value;
        this.resourceId = Optional.empty();
        this.diskContainerPath = Optional.empty();
        this.diskPersistenceId = Optional.empty();
        this.diskMountInfo = Optional.empty();
    }

    public ResourceBuilder setValue(Value value) {
        this.value = value;
        return this;
    }

    public ResourceBuilder setResourceId(String resourceId) {
        this.resourceId = Optional.of(resourceId);
        return this;
    }

    public ResourceBuilder clearResourceId() {
        this.resourceId = Optional.empty();
        return this;
    }

    public ResourceBuilder clearPersistenceId() {
        this.diskPersistenceId = Optional.empty();
        return this;
    }

    public ResourceBuilder setRootVolume(String containerPath, Optional<String> existingPersistenceId) {
        if (!this.resourceName.equals(DISK_RESOURCE_TYPE)) {
            throw new IllegalStateException(
                    "Refusing to set disk information against resource of type: " + resourceName);
        }
        this.diskContainerPath = Optional.of(containerPath);
        this.diskPersistenceId = existingPersistenceId;
        return this;
    }

    public ResourceBuilder setMountVolume(
            String containerPath, Optional<String> existingPersistenceId, Optional<String> existingMountRoot) {
        setRootVolume(containerPath, existingPersistenceId);
        DiskInfo.Source.Builder sourceBuilder = DiskInfo.Source.newBuilder().setType(DiskInfo.Source.Type.MOUNT);
        if (existingMountRoot.isPresent()) {
            sourceBuilder.getMountBuilder().setRoot(existingMountRoot.get());
        }
        this.diskMountInfo = Optional.of(sourceBuilder.build());
        return this;
    }

    public Resource build() {
        Resource.Builder builder = Resource.newBuilder()
                .setName(resourceName)
                .setRole(role);
        if (principal.isPresent()) {
            builder.getReservationBuilder().setPrincipal(principal.get());
            if (resourceId.isPresent()) {
                builder.getReservationBuilder().getLabelsBuilder().addLabelsBuilder()
                        .setKey(MesosResource.RESOURCE_ID_KEY)
                        .setValue(resourceId.get());
            }
            if (diskContainerPath.isPresent()) {
                DiskInfo.Builder diskBuilder = builder.getDiskBuilder();
                diskBuilder.getVolumeBuilder()
                        .setContainerPath(diskContainerPath.get())
                        .setMode(Volume.Mode.RW);
                diskBuilder.getPersistenceBuilder()
                        .setPrincipal(principal.get())
                        .setId(diskPersistenceId.isPresent() ? diskPersistenceId.get() : "");
                if (diskMountInfo.isPresent()) {
                    diskBuilder.setSource(diskMountInfo.get());
                }
            }
        }
        return setValue(builder, value).build();
    }

    private static Optional<String> getPrincipal(Resource resource) {
        return resource.hasReservation()
                ? Optional.of(resource.getReservation().getPrincipal())
                : Optional.empty();
    }

    private static Value getValue(Resource resource) {
        Value.Builder builder = Value.newBuilder()
                .setType(resource.getType());
        switch (resource.getType()) {
        case SCALAR:
            return builder.setScalar(resource.getScalar()).build();
        case RANGES:
            return builder.setRanges(resource.getRanges()).build();
        case SET:
            return builder.setSet(resource.getSet()).build();
        default:
            throw new IllegalArgumentException(
                    String.format("Unsupported resource value type: %s", resource.getType()));
        }
    }

    private static Resource.Builder setValue(Resource.Builder builder, Value value) {
        builder.setType(value.getType());
        switch (value.getType()) {
        case SCALAR:
            builder.setScalar(value.getScalar());
            return builder;
        case RANGES:
            builder.setRanges(value.getRanges());
            return builder;
        case SET:
            builder.setSet(value.getSet());
            return builder;
        default:
            throw new IllegalArgumentException(String.format("Unsupported spec value type: %s", value.getType()));
        }
    }
}
