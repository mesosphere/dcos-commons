package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.DefaultVolumeSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.VolumeSpec;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Constructs Mesos {@link Resource} protobufs.
 */
public final class ResourceBuilder {
  private final String resourceName;

  private final String preReservedRole;

  private Optional<String> principal;

  private Protos.Value value;

  private Optional<String> role;

  private Optional<String> resourceId;

  private Optional<String> resourceNamespace;

  private Optional<String> diskContainerPath;

  private Optional<String> diskPersistenceId;

  private Optional<Protos.ResourceProviderID> providerId;

  private Optional<Protos.Resource.DiskInfo.Source> diskSource;

  private Optional<MesosResource> mesosResource;

  private ResourceBuilder(String resourceName, Protos.Value value, String preReservedRole) {
    this.resourceName = resourceName;
    this.value = value;
    this.preReservedRole = preReservedRole;
    this.role = Optional.empty();
    this.principal = Optional.empty();
    this.resourceId = Optional.empty();
    this.resourceNamespace = Optional.empty();
    this.diskContainerPath = Optional.empty();
    this.diskPersistenceId = Optional.empty();
    this.providerId = Optional.empty();
    this.diskSource = Optional.empty();
    this.mesosResource = Optional.empty();
  }

  public static ResourceBuilder fromSpec(
      ResourceSpec spec, Optional<String> resourceId, Optional<String> resourceNamespace)
  {
    ResourceBuilder builder =
        new ResourceBuilder(spec.getName(), spec.getValue(), spec.getPreReservedRole())
            .setRole(Optional.of(spec.getRole()))
            .setPrincipal(Optional.of(spec.getPrincipal()));
    resourceId.ifPresent(builder::setResourceId);
    resourceNamespace.ifPresent(builder::setResourceNamespace);
    return builder;
  }

  public static ResourceBuilder fromSpec(
      VolumeSpec spec,
      Optional<String> resourceId,
      Optional<String> resourceNamespace,
      Optional<String> persistenceId,
      Optional<Protos.ResourceProviderID> providerId,
      Optional<Protos.Resource.DiskInfo.Source> diskSource)
  {
    ResourceBuilder resourceBuilder = fromSpec(spec, resourceId, resourceNamespace);

    providerId.ifPresent(resourceBuilder::setProviderId);

    switch (spec.getType()) {
      case ROOT:
        if (diskSource.isPresent()) {
          throw new IllegalStateException("Source must not be set on a ROOT volume");
        }
        return resourceBuilder.setRootVolume(spec.getContainerPath(), persistenceId);
      case MOUNT:
        if (!diskSource.isPresent()) {
          throw new IllegalStateException("Source must be set on a MOUNT volume");
        }
        return resourceBuilder
            .setMountVolume(spec.getContainerPath(), persistenceId, diskSource.get());
      default:
        throw new IllegalStateException(String.format("Unexpected disk type: %s", spec.getType()));
    }
  }

  public static ResourceBuilder fromExistingResource(Protos.Resource resource) {
    if (!resource.hasDisk()) {
      return fromSpec(
          getResourceSpec(resource),
          ResourceUtils.getResourceId(resource),
          ResourceUtils.getNamespace(resource));
    } else {
      return fromSpec(
          getVolumeSpec(resource),
          ResourceUtils.getResourceId(resource),
          ResourceUtils.getNamespace(resource),
          ResourceUtils.getPersistenceId(resource),
          ResourceUtils.getProviderId(resource),
          ResourceUtils.getDiskSource(resource));
    }
  }

  public static ResourceBuilder fromUnreservedValue(String resourceName, Protos.Value value) {
    return new ResourceBuilder(resourceName, value, Constants.ANY_ROLE);
  }

  @SuppressWarnings("deprecation")
  private static ResourceSpec getResourceSpec(Protos.Resource resource) {
    if (!ResourceUtils.hasResourceId(resource)) {
      throw new IllegalStateException(
          "Cannot generate resource spec from resource which has not been reserved by the SDK.");
    }

    return DefaultResourceSpec.newBuilder()
        .name(resource.getName())
        .value(ValueUtils.getValue(resource))
        .role(ResourceUtils.getRole(resource))
        .preReservedRole(resource.getRole())
        .principal(ResourceUtils.getPrincipal(resource).get())
        .build();
  }

  @SuppressWarnings("deprecation")
  private static VolumeSpec getVolumeSpec(Protos.Resource resource) {
    return resource.getDisk().hasSource()
        ? DefaultVolumeSpec.createMountVolume(
        resource.getScalar().getValue(),
        resource.getDisk().getVolume().getContainerPath(),
        resource.getDisk().getSource().hasProfile()
            ? Collections.singletonList(resource.getDisk().getSource().getProfile())
            : Collections.emptyList(),
        ResourceUtils.getRole(resource),
        resource.getRole(),
        resource.getDisk().getPersistence().getPrincipal())
        : DefaultVolumeSpec.createRootVolume(
        resource.getScalar().getValue(),
        resource.getDisk().getVolume().getContainerPath(),
        ResourceUtils.getRole(resource),
        resource.getRole(),
        resource.getDisk().getPersistence().getPrincipal());
  }

  private static Protos.Resource.Builder setValue(
      Protos.Resource.Builder builder,
      Protos.Value value)
  {
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
        throw new IllegalArgumentException(
            String.format("Unsupported spec value type: %s", value.getType())
        );
    }
  }

  /**
   * Sets the value for this resource. Supported types are {@code SCALAR}, {@code RANGES}, and {@code SET}.
   */
  public ResourceBuilder setValue(Protos.Value value) {
    this.value = value;
    return this;
  }

  /**
   * Assigns the resource id to the provided value.
   */
  public ResourceBuilder setResourceId(String resourceId) {
    this.resourceId = Optional.of(resourceId);
    return this;
  }

  /**
   * Assigns the resource namespace to the provided value.
   */
  private ResourceBuilder setResourceNamespace(String resourceNamespace) {
    this.resourceNamespace = Optional.of(resourceNamespace);
    return this;
  }

  /**
   * Assigns the resource provider id to the provided value.
   */
  private ResourceBuilder setProviderId(Protos.ResourceProviderID providerId) {
    this.providerId = Optional.of(providerId);
    return this;
  }

  /**
   * Assigns information relating to {@code ROOT} disk volumes for this resource.
   *
   * @param existingPersistenceId the persistence ID of a previously reserved disk resource to be associated with
   * @throws IllegalStateException if the resource does not have type {@code disk}
   */
  public ResourceBuilder setRootVolume(
      String containerPath,
      Optional<String> existingPersistenceId)
  {
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
      String containerPath,
      Optional<String> existingPersistenceId,
      Protos.Resource.DiskInfo.Source diskSource)
  {
    // common information across ROOT + MOUNT volumes:
    setRootVolume(containerPath, existingPersistenceId);
    // additional information specific to MOUNT volumes:
    if (diskSource.getType() != Protos.Resource.DiskInfo.Source.Type.MOUNT) {
      throw new IllegalStateException(
          String.format("Expecting disk source to be of type MOUNT: %s", diskSource)
      );
    }
    this.diskSource = Optional.of(diskSource);
    return this;
  }

  public ResourceBuilder setMesosResource(MesosResource mesosResource) {
    this.mesosResource = Optional.of(mesosResource);
    return this;
  }

  @SuppressWarnings("deprecation")
  public Protos.Resource build() {
    // Note:
    // In the pre-resource-refinment world (< 1.9), Mesos will expect
    // reserved Resources to have role and reservation set.
    //
    // In the post-resource-refinement world (1.10+), Mesos will expect
    // reserved Resources to have reservations (and ONLY reservations) set.
    Protos.Resource.Builder builder = mesosResource
        .map(mesosResource1 -> mesosResource1.getResource().toBuilder().clearAllocationInfo())
        .orElseGet(Protos.Resource::newBuilder);
    builder.setName(resourceName)
        .setRole(Constants.ANY_ROLE)
        .setType(value.getType());

    boolean preReservedSupported = Capabilities.getInstance().supportsPreReservedResources();

    // Set the reservation (<1.9) or reservations (1.10+) for Resources that do not
    // already have a resource id.
    // todo (bwood): @gabriel, why do we not just re-run if resource id is already set?
    // is the reservation setting destructive / non-repeatable?
    if (role.isPresent() && !ResourceUtils.hasResourceId(builder.build())) {
      if (preReservedSupported) {
        if (!preReservedRole.equals(Constants.ANY_ROLE) && !mesosResource.isPresent()) {
          builder.addReservations(Protos.Resource.ReservationInfo.newBuilder()
              .setRole(preReservedRole)
              .setType(Protos.Resource.ReservationInfo.Type.STATIC));
        }
        builder.addReservations(getRefinedReservationInfo());
      } else {
        builder.setReservation(getLegacyReservationInfo());
      }
    }

    // Set the role (<1.9) or clear it (1.10+) for reserved resources.
    if (role.isPresent() && !preReservedSupported) {
      builder.setRole(role.get());
    } else if (preReservedSupported && builder.getReservationsCount() > 0) {
      builder.clearRole();
    }

    providerId.ifPresent(builder::setProviderId);

    if (diskContainerPath.isPresent()) {
      Protos.Resource.DiskInfo.Builder diskBuilder = builder.getDiskBuilder();
      diskBuilder
          .getVolumeBuilder()
          .setContainerPath(diskContainerPath.get())
          .setMode(Protos.Volume.Mode.RW);
      diskBuilder
          .getPersistenceBuilder()
          .setPrincipal(principal.get())
          .setId(diskPersistenceId.orElseGet(() -> UUID.randomUUID().toString()));
      diskSource.ifPresent(diskBuilder::setSource);
    }

    return setValue(builder, value).build();
  }

  private Protos.Resource.ReservationInfo getRefinedReservationInfo() {
    return withReservationLabels(Protos.Resource.ReservationInfo.newBuilder()
        .setRole(role.get())
        .setType(Protos.Resource.ReservationInfo.Type.DYNAMIC)
        .setPrincipal(principal.get()));
  }

  private Protos.Resource.ReservationInfo getLegacyReservationInfo() {
    return withReservationLabels(Protos.Resource.ReservationInfo.newBuilder()
        .setPrincipal(principal.get()));
  }

  private Protos.Resource.ReservationInfo withReservationLabels(
      Protos.Resource.ReservationInfo.Builder reservationBuilder)
  {
    // If resourceId is empty, create a new resourceId:
    AuxLabelAccess.setResourceId(
        reservationBuilder,
        resourceId.orElseGet(() -> UUID.randomUUID().toString())
    );
    resourceNamespace.ifPresent(s -> AuxLabelAccess.setResourceNamespace(reservationBuilder, s));
    return reservationBuilder.build();
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
