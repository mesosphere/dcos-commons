package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.VolumeSpec;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Optional;
import java.util.UUID;

/**
 * Constructs Mesos {@link Resource} protobufs.
 */
public class ResourceBuilder {
    private final String resourceName;
    private Optional<String> principal;
    private Protos.Value value;
    private Optional<String> role;
    private final String preReservedRole;
    private Optional<String> resourceId;
    private Optional<String> diskContainerPath;
    private Optional<String> diskPersistenceId;
    private Optional<Protos.Resource.DiskInfo.Source> diskMountInfo;
    private MesosResource mesosResource;

    /**
     * Creates a new builder using basic resource information in the provided {@link ResourceSpec}.
     *
     * @throws IllegalArgumentException if the provided {@code spec} is a {@link VolumeSpec}
     */
    public static ResourceBuilder fromSpec(ResourceSpec spec, Optional<String> resourceId) {
        if (spec instanceof VolumeSpec) {
            throw new IllegalArgumentException(
                    "Provided VolumeSpec must be passed to one of the VolumeSpec-specific functions: " +
                    spec.toString());
        }
        return fromSpecInternal(spec, resourceId);
    }

    /**
     * Creates a new builder using root volume-specific information in the provided {@link ResourceSpec}.
     *
     * @throws IllegalArgumentException if the provided {@code spec} is for a mount volume
     */
    public static ResourceBuilder fromRootVolumeSpec(
            VolumeSpec spec, Optional<String> resourceId, Optional<String> persistenceId) {
        if (spec.getType() != VolumeSpec.Type.ROOT) {
            throw new IllegalArgumentException("Provided VolumeSpec must be of type ROOT: " + spec.toString());
        }
        return fromSpecInternal(spec, resourceId).setRootVolume(spec.getContainerPath(), persistenceId);
    }

    /**
     * Creates a new builder using mount volume-specific information in the provided {@link ResourceSpec}.
     *
     * @throws IllegalArgumentException if the provided {@code spec} is for a root volume
     */
    public static ResourceBuilder fromMountVolumeSpec(
            VolumeSpec spec, Optional<String> resourceId, Optional<String> persistenceId, String mountRoot) {
        if (spec.getType() != VolumeSpec.Type.MOUNT) {
            throw new IllegalArgumentException("Provided VolumeSpec must be of type MOUNT: " + spec.toString());
        }
        return fromSpecInternal(spec, resourceId).setMountVolume(spec.getContainerPath(), persistenceId, mountRoot);
    }

    private static ResourceBuilder fromSpecInternal(ResourceSpec spec, Optional<String> resourceId) {
        return new ResourceBuilder(spec.getName(), spec.getValue(), spec.getPreReservedRole())
                .setRole(Optional.of(spec.getRole()))
                .setPrincipal(Optional.of(spec.getPrincipal()))
                .setResourceId(resourceId);
    }

    /**
     * Pre-populates a new {@link ResourceBuilder} instance using the details in the provided Mesos
     * {@link Protos.Resource} which must have been reserved by the SDK.
     */
    public static ResourceBuilder fromExistingResource(Protos.Resource resource) {
        Optional<String> resourceId = ResourceUtils.getResourceId(resource);
        if (!resourceId.isPresent()) {
            throw new IllegalStateException(
                    "Cannot generate resource spec from resource which has not been reserved by the SDK.");
        }

        ResourceBuilder builder = new ResourceBuilder(
                resource.getName(), ValueUtils.getValue(resource), ResourceUtils.getRootRole(resource))
                .setRole(Optional.of(ResourceUtils.getRole(resource)))
                .setPrincipal(ResourceUtils.getPrincipal(resource))
                .setResourceId(resourceId);
        if (resource.hasDisk()) {
            if (resource.getDisk().hasSource()) {
                builder.setMountVolume(
                        resource.getDisk().getVolume().getContainerPath(),
                        ResourceUtils.getPersistenceId(resource),
                        ResourceUtils.getSourceRoot(resource).get());
            } else {
                builder.setRootVolume(
                        resource.getDisk().getVolume().getContainerPath(),
                        ResourceUtils.getPersistenceId(resource));
            }
        }
        return builder;
    }

    public static ResourceBuilder fromUnreservedValue(String resourceName, Protos.Value value) {
        return new ResourceBuilder(resourceName, value, Constants.ANY_ROLE);
    }

    private ResourceBuilder(String resourceName, Protos.Value value, String preReservedRole) {
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
    public ResourceBuilder setValue(Protos.Value value) {
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
     * Assigns information relating to {@code ROOT} disk volumes for this resource.
     *
     * @param existingPersistenceId the persistence ID of a previously reserved disk resource to be associated with
     * @throws IllegalStateException if the resource does not have type {@code disk}
     */
    private ResourceBuilder setRootVolume(String containerPath, Optional<String> existingPersistenceId) {
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
    private ResourceBuilder setMountVolume(
            String containerPath, Optional<String> existingPersistenceId, String mountRoot) {
        // common information across ROOT + MOUNT volumes:
        setRootVolume(containerPath, existingPersistenceId);
        // additional information specific to MOUNT volumes:
        Protos.Resource.DiskInfo.Source.Builder sourceBuilder = Protos.Resource.DiskInfo.Source.newBuilder()
                .setType(Protos.Resource.DiskInfo.Source.Type.MOUNT);
        sourceBuilder.getMountBuilder().setRoot(mountRoot);
        this.diskMountInfo = Optional.of(sourceBuilder.build());
        return this;
    }

    public ResourceBuilder setMesosResource(MesosResource mesosResource) {
        this.mesosResource = mesosResource;
        return this;
    }

    public ResourceBuilder setRole(Optional<String> role) {
        this.role = role;
        return this;
    }

    public ResourceBuilder setPrincipal(Optional<String> principal) {
        this.principal = principal;
        return this;
    }

    public Protos.Resource build() {
        // Note:
        // In the pre-resource-refinment world (< 1.9), Mesos will expect
        // reserved Resources to have role and reservation set.
        //
        // In the post-resource-refinement world (1.10+), Mesos will expect
        // reserved Resources to have reservations (and ONLY reservations) set.
        Protos.Resource.Builder builder =
                mesosResource == null ? Protos.Resource.newBuilder() : mesosResource.getResource().toBuilder();
        builder.setName(resourceName)
                .setType(value.getType());

        boolean preReservedSupported = Capabilities.getInstance().supportsPreReservedResources();
        final Optional<String> rootRoleToUse;
        if (preReservedSupported) {
            // 1.10+ uses prereservation as a list of hierarchical roles, clear root role of "*"
            if (builder.getReservationsCount() > 0) {
                rootRoleToUse = Optional.empty();
            } else {
                rootRoleToUse = Optional.of(Constants.ANY_ROLE);
            }
        } else {
            // <=1.9 doesn't support prereservation, use root role of "*" or specified role if any
            if (role.isPresent()) {
                rootRoleToUse = role;
            } else {
                rootRoleToUse = Optional.of(Constants.ANY_ROLE);
            }
        }
        builder = ResourceUtils.setRootRole(builder, rootRoleToUse);

        // Set the reservation (<1.9) or reservations (1.10+) for Resources that do not
        // already have a resource id.
        // todo (bwood): @gabriel, why do we not just re-run if resource id is already set?
        // is the reservation setting destructive / non-repeatable?
        if (role.isPresent() && !ResourceUtils.hasResourceId(builder.build())) {
            String resId = resourceId.isPresent() ? resourceId.get() : UUID.randomUUID().toString();
            Protos.Resource.ReservationInfo reservationInfo = getReservationInfo(role.get(), resId);

            if (preReservedSupported) {
                if (!preReservedRole.equals(Constants.ANY_ROLE) && mesosResource == null) {
                    builder.addReservationsBuilder()
                            .setRole(preReservedRole)
                            .setType(Protos.Resource.ReservationInfo.Type.STATIC);
                }
                builder.addReservations(reservationInfo);
            } else {
                builder.setReservation(reservationInfo);
            }
        }

        if (diskContainerPath.isPresent()) {
            Protos.Resource.DiskInfo.Builder diskBuilder = builder.getDiskBuilder();
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

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    private Protos.Resource.ReservationInfo getReservationInfo(String role, String resId) {
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            return getRefinedReservationInfo(role, resId);
        } else {
            return getLegacyReservationInfo(resId);
        }
    }

    private Protos.Resource.ReservationInfo getRefinedReservationInfo(String role, String resId) {
        Protos.Resource.ReservationInfo.Builder reservationBuilder = Protos.Resource.ReservationInfo.newBuilder()
                .setRole(role)
                .setType(Protos.Resource.ReservationInfo.Type.DYNAMIC)
                .setPrincipal(principal.get());
        AuxLabelAccess.setResourceId(reservationBuilder, resId);
        return reservationBuilder.build();
    }

    private Protos.Resource.ReservationInfo getLegacyReservationInfo(String resId) {
        Protos.Resource.ReservationInfo.Builder reservationBuilder = Protos.Resource.ReservationInfo.newBuilder()
                .setPrincipal(principal.get());
        AuxLabelAccess.setResourceId(reservationBuilder, resId);
        return reservationBuilder.build();
    }

    private static Protos.Resource.Builder setValue(Protos.Resource.Builder builder, Protos.Value value) {
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
