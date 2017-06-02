package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.DefaultVolumeSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Value;

import java.util.Optional;
import java.util.UUID;

/**
 * Constructs Mesos {@link Resource} protobufs.
 */
public class ResourceBuilder {
    private final String resourceName;
    private Optional<String> principal;
    private Value value;
    private Optional<String> role;
    private final String preReservedRole;
    private Optional<String> resourceId;
    private Optional<String> diskContainerPath;
    private Optional<String> diskPersistenceId;
    private Optional<DiskInfo.Source> diskMountInfo;

    public static ResourceBuilder fromSpec(ResourceSpec spec, Optional<String> resourceId) {
        return new ResourceBuilder(spec.getName(), spec.getValue(), spec.getPreReservedRole())
                .setRole(Optional.of(spec.getRole()))
                .setPrincipal(Optional.of(spec.getPrincipal()))
                .setResourceId(resourceId);
    }

    public static ResourceBuilder fromSpec(
            VolumeSpec spec,
            Optional<String> resourceId,
            Optional<String> persistenceId,
            Optional<String> sourceRoot) {

        ResourceBuilder resourceBuilder = fromSpec((ResourceSpec) spec, resourceId);
        switch (spec.getType()) {
            case ROOT:
                return resourceBuilder.setRootVolume(spec.getContainerPath(), persistenceId);
            case MOUNT:
                if (!sourceRoot.isPresent()) {
                    throw new IllegalStateException("Source path must be set on MOUNT volumes.");
                }
                return resourceBuilder.setMountVolume(spec.getContainerPath(), persistenceId, sourceRoot);
            default:
                throw new IllegalStateException(String.format("Unexpected disk type: %s", spec.getType()));
        }
    }

    public static ResourceBuilder fromExistingResource(Resource resource) {
        Optional<String> resourceId = ResourceCollectionUtils.getResourceId(resource);

        if (!resource.hasDisk()) {
            ResourceSpec resourceSpec = getResourceSpec(resource);
            return fromSpec(resourceSpec, resourceId);
        } else {
            VolumeSpec volumeSpec = getVolumeSpec(resource);
            Optional<String> persistenceId = ResourceCollectionUtils.getPersistenceId(resource);
            Optional<String> sourceRoot = ResourceCollectionUtils.getSourceRoot(resource);
            return fromSpec(volumeSpec, resourceId, persistenceId, sourceRoot);
        }
    }

    public static ResourceBuilder fromUnreservedValue(String resourceName, Value value) {
        return new ResourceBuilder(resourceName, value, Constants.ANY_ROLE);
    }

    private static ResourceSpec getResourceSpec(Resource resource) {
        if (!resource.hasReservation()) {
            throw new IllegalStateException(
                    "Cannot generate resource spec from resource which has not been reserved by the SDK.");
        }

        return new DefaultResourceSpec(
                resource.getName(),
                ValueUtils.getValue(resource),
                ResourceCollectionUtils.getRole(resource).get(),
                resource.getRole(),
                ResourceCollectionUtils.getPrincipal(resource).get(),
                ""); // env-key isn't used
    }

    private static VolumeSpec getVolumeSpec(Resource resource) {
        VolumeSpec.Type type = resource.getDisk().hasSource() ? VolumeSpec.Type.MOUNT : VolumeSpec.Type.ROOT;
        return new DefaultVolumeSpec(
                resource.getScalar().getValue(),
                type,
                resource.getDisk().getVolume().getContainerPath(),
                ResourceCollectionUtils.getRole(resource).get(),
                resource.getRole(),
                resource.getDisk().getPersistence().getPrincipal(),
                ""); // env-key isn't used
    }

    private ResourceBuilder(String resourceName, Value value, String preReservedRole) {
        this.resourceName = resourceName;
        this.value = value;
        this.preReservedRole = preReservedRole;
        this.role = Optional.empty();
        this.principal = Optional.empty();
        this.resourceId = Optional.empty();
        this.diskContainerPath = Optional.empty();
        this.diskPersistenceId = Optional.empty();
        this.diskMountInfo = Optional.empty();
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
    public ResourceBuilder setResourceId(Optional<String> resourceId) {
        this.resourceId = resourceId;
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
        if (!this.resourceName.equals(Constants.DISK_RESOURCE_TYPE)) {
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
                .setRole(preReservedRole);

        if (role.isPresent()) {
            String resId = resourceId.isPresent() ? resourceId.get() : UUID.randomUUID().toString();
            Resource.ReservationInfo reservationInfo = Resource.ReservationInfo.newBuilder()
                    .setPrincipal(principal.get())
                    .setLabels(
                            Protos.Labels.newBuilder()
                                    .addLabels(Protos.Label.newBuilder()
                                            .setKey(MesosResource.RESOURCE_ID_KEY)
                                            .setValue(resId)))
                    .build();
            builder.setRole(role.get());
            builder.setReservation(reservationInfo);
        }

        if (diskContainerPath.isPresent()) {
            DiskInfo.Builder diskBuilder = builder.getDiskBuilder();
            diskBuilder.getVolumeBuilder()
                    .setContainerPath(diskContainerPath.get())
                    .setMode(Protos.Volume.Mode.RW);
            diskBuilder.getPersistenceBuilder()
                    .setPrincipal(principal.get())
                    .setId(diskPersistenceId.isPresent() ? diskPersistenceId.get() : UUID.randomUUID().toString());
            if (diskMountInfo.isPresent()) {
                diskBuilder.setSource(diskMountInfo.get());
            }
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

    public ResourceBuilder setRole(Optional<String> role) {
        this.role = role;
        return this;
    }

    public ResourceBuilder setPrincipal(Optional<String> principal) {
        this.principal = principal;
        return this;
    }
}
