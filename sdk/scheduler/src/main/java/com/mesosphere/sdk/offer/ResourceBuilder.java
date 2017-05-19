package com.mesosphere.sdk.offer;

import java.util.Optional;

import org.apache.commons.lang3.builder.ToStringBuilder;
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


    private String role;
    private Optional<String> principal;
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
        Optional<String> principal = resource.hasReservation() && resource.getReservation().hasPrincipal()
                ? Optional.of(resource.getReservation().getPrincipal())
                : Optional.empty();

        ResourceBuilder builder =
                new ResourceBuilder(resource.getRole(), principal, resource.getName(), getValue(resource));

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

    /**
     * Sets the role for this resource. This may initially be the role under which a resource was offered, e.g. "*".
     * When resource reservation information is being configured, this should be populated with the resource role being
     * used by the service.
     */
    public ResourceBuilder setRole(String role) {
        this.role = role;
        return this;
    }

    /**
     * Sets the principal for reserving this resource. This may initially be unset when a resource was offered.
     * When resource reservation information is being configured, this should be populated with the principal being
     * used by the service.
     */
    public ResourceBuilder setPrincipal(String principal) {
        this.principal = Optional.of(principal);
        return this;
    }

    /**
     * Sets the value for this resource. Supported types are {@code SCALAR}, {@code RANGES}, and {@code SET}.
     */
    public ResourceBuilder setValue(Value value) {
        this.value = value;
        return this;
    }

    /**
     * Assigns a unique resource ID for this resource, which is used to uniquely identify it in later offer evaluation
     * runs. This may be used with e.g. restarting a task at its prior location.
     */
    public ResourceBuilder setResourceId(String resourceId) {
        this.resourceId = Optional.of(resourceId);
        return this;
    }

    /**
     * Clears a previously set resource ID, or does nothing if the resource ID is already unset. This may be used to
     * disassociate a task with a given reserved resource, e.g. with task replacement.
     */
    public ResourceBuilder clearResourceId() {
        this.resourceId = Optional.empty();
        return this;
    }

    /**
     * Clears a previously set disk persistence ID, or does nothing if the disk persistence ID is already unset.
     */
    public ResourceBuilder clearPersistenceId() {
        this.diskPersistenceId = Optional.empty();
        return this;
    }

    /**
     * Assigns information relating to {@code ROOT} disk volumes for this resource.
     *
     * @param existingPersistenceId the persistence ID of a previously reserved disk resource to be associated with
     * @throws IllegalStateException if the resource does not have type {@code disk}
     */
    public ResourceBuilder setRootVolume(String containerPath, Optional<String> existingPersistenceId) {
        if (!this.resourceName.equals(DISK_RESOURCE_TYPE)) {
            throw new IllegalStateException(
                    "Refusing to set disk information against resource of type: " + resourceName);
        }
        this.diskContainerPath = Optional.of(containerPath);
        this.diskPersistenceId = existingPersistenceId;
        return this;
    }

    /**
     * Assigns information relating to {@code MOUNT} disk volumes for this resource.
     *
     * @param existingPersistenceId the persistence ID of a previously reserved disk resource to be associated with
     * @throws IllegalStateException if the resource does not have type {@code disk}
     */
    public ResourceBuilder setMountVolume(
            String containerPath, Optional<String> existingPersistenceId, Optional<String> existingMountRoot) {
        // common information across ROOT + MOUNT volumes:
        setRootVolume(containerPath, existingPersistenceId);
        // additional information specific to MOUNT volumes:
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
        if (resourceId.isPresent()) {
            builder.getReservationBuilder().getLabelsBuilder().addLabelsBuilder()
                    .setKey(MesosResource.RESOURCE_ID_KEY)
                    .setValue(resourceId.get());
        }
        if (principal.isPresent()) {
            builder.getReservationBuilder().setPrincipal(principal.get());
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
        } else if (diskContainerPath.isPresent()) {
            throw new IllegalStateException(
                    "Principal is required when disk information has been specified: " + toString());
        }
        return setValue(builder, value).build();
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

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
