package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
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
    private MesosResource mesosResource;

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

        ResourceBuilder resourceBuilder = fromSpec(spec, resourceId);
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
        Optional<String> resourceId = ResourceUtils.getResourceId(resource);

        if (!resource.hasDisk()) {
            ResourceSpec resourceSpec = getResourceSpec(resource);
            return fromSpec(resourceSpec, resourceId);
        } else {
            VolumeSpec volumeSpec = getVolumeSpec(resource);
            Optional<String> persistenceId = ResourceUtils.getPersistenceId(resource);
            Optional<String> sourceRoot = ResourceUtils.getSourceRoot(resource);
            return fromSpec(volumeSpec, resourceId, persistenceId, sourceRoot);
        }
    }

    public static ResourceBuilder fromUnreservedValue(String resourceName, Value value) {
        return new ResourceBuilder(resourceName, value, Constants.ANY_ROLE);
    }

    private static ResourceSpec getResourceSpec(Resource resource) {
        if (!ResourceUtils.hasResourceId(resource)) {
            throw new IllegalStateException(
                    "Cannot generate resource spec from resource which has not been reserved by the SDK.");
        }

        return new DefaultResourceSpec(
                resource.getName(),
                ValueUtils.getValue(resource),
                ResourceUtils.getRole(resource),
                resource.getRole(),
                ResourceUtils.getPrincipal(resource).get());
    }

    private static VolumeSpec getVolumeSpec(Resource resource) {
        VolumeSpec.Type type = resource.getDisk().hasSource() ? VolumeSpec.Type.MOUNT : VolumeSpec.Type.ROOT;
        return new DefaultVolumeSpec(
                resource.getScalar().getValue(),
                type,
                resource.getDisk().getVolume().getContainerPath(),
                ResourceUtils.getRole(resource),
                resource.getRole(),
                resource.getDisk().getPersistence().getPrincipal());
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

    public ResourceBuilder setMesosResource(MesosResource mesosResource) {
        this.mesosResource = mesosResource;
        return this;
    }

    public Resource build() {
        // Note:
        // In the pre-resource-refinment world (< 1.9), Mesos will expect
        // reserved Resources to have role and reservation set.
        //
        // In the post-resource-refinement world (1.10+), Mesos will expect
        // reserved Resources to have reservations (and ONLY reservations) set.
        Resource.Builder builder =
                mesosResource == null ? Resource.newBuilder() : mesosResource.getResource().toBuilder();
        builder.setName(resourceName)
                .setRole(Constants.ANY_ROLE)
                .setType(value.getType());

        boolean preReservedSupported = Capabilities.getInstance().supportsPreReservedResources();

        // Set the reservation (<1.9) or reservations (1.10+) for Resources that do not
        // already have a resource id.
        // todo (bwood): @gabriel, why do we not just re-run if resource id is already set?
        // is the reservation setting destructive / non-repeatable?
        if (role.isPresent() && !ResourceUtils.hasResourceId(builder.build())) {
            String resId = resourceId.isPresent() ? resourceId.get() : UUID.randomUUID().toString();
            Resource.ReservationInfo reservationInfo = getReservationInfo(role.get(), resId);

            if (preReservedSupported) {
                if (!preReservedRole.equals(Constants.ANY_ROLE) && mesosResource == null) {
                    builder.addReservations(
                            Resource.ReservationInfo.newBuilder()
                            .setRole(preReservedRole)
                            .setType(Resource.ReservationInfo.Type.STATIC));
                }
                builder.addReservations(reservationInfo);
            } else {
                builder.setReservation(reservationInfo);
            }
        }

        // Set the role (<1.9) or clear it (1.10+) for reserved resources.
        if (role.isPresent() && !preReservedSupported) {
            builder.setRole(role.get());
        } else if (preReservedSupported && builder.getReservationsCount() > 0) {
            builder.clearRole();
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

    private Resource.ReservationInfo getReservationInfo(String role, String resId) {
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            return getRefinedReservationInfo(role, resId);
        } else {
            return getLegacyReservationInfo(resId);
        }
    }

    private Resource.ReservationInfo getRefinedReservationInfo(String role, String resId) {
        Resource.ReservationInfo.Builder reservationBuilder = Resource.ReservationInfo.newBuilder()
                .setRole(role)
                .setType(Resource.ReservationInfo.Type.DYNAMIC)
                .setPrincipal(principal.get());
        AuxLabelAccess.setResourceId(reservationBuilder, resId);
        return reservationBuilder.build();
    }

    private Resource.ReservationInfo getLegacyReservationInfo(String resId) {
        Resource.ReservationInfo.Builder reservationBuilder = Resource.ReservationInfo.newBuilder()
                .setPrincipal(principal.get());
        AuxLabelAccess.setResourceId(reservationBuilder, resId);
        return reservationBuilder.build();
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
