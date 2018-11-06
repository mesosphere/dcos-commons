package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * Pairs a {@link ResourceSpec} definition with an existing task's labels associated with that resource.
 */
class ResourceLabels {
  private final ResourceSpec original;

  private final ResourceSpec updated;

  private final String resourceId;

  private final Optional<String> resourceNamespace;

  private final Optional<String> persistenceId;

  private final Optional<Protos.ResourceProviderID> providerId;

  private final Optional<Protos.Resource.DiskInfo.Source> diskSource;

  ResourceLabels(ResourceSpec resourceSpec, String resourceId, Optional<String> resourceNamespace) {
    this(
        resourceSpec,
        resourceSpec,
        resourceId,
        resourceNamespace,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /**
   * ResourceLabels are used to map {@link com.mesosphere.sdk.specification.ServiceSpec} entities to resources which
   * have actually been consumed.  Generally speaking there should be no difference between the resources from a Mesos
   * perspective and the resources from a {@link com.mesosphere.sdk.specification.ServiceSpec} perspective.
   * <p>
   * There is one special case in which they differ, MOUNT volume resources.  In this case the {@link ResourceSpec}
   * and actually consumed resources differ in value.  Because MOUNT volumes are atomic if the {@link ResourceSpec}
   * indicates a need for 25 GB of disk and 50 GB of disk are actually offered, then 50 GB must be consumed.
   * <p>
   * To capture this change the concept of an "updated" ResourceSpec is needed.  We modify the ResourceSpec in this
   * scenario to match the actually consumed resources to facilitate consistent protobuf construction.
   */
  ResourceLabels(
      ResourceSpec original,
      ResourceSpec updated,
      String resourceId,
      Optional<String> resourceNamespace,
      Optional<String> persistenceId,
      Optional<Protos.ResourceProviderID> providerId,
      Optional<Protos.Resource.DiskInfo.Source> diskSource)
  {
    this.original = original;
    this.updated = updated;
    this.resourceId = resourceId;
    this.resourceNamespace = resourceNamespace;
    this.persistenceId = persistenceId;
    this.providerId = providerId;
    this.diskSource = diskSource;
  }

  public ResourceSpec getOriginal() {
    return original;
  }

  public ResourceSpec getUpdated() {
    return updated;
  }

  public String getResourceId() {
    return resourceId;
  }

  public Optional<String> getResourceNamespace() {
    return resourceNamespace;
  }

  public Optional<String> getPersistenceId() {
    return persistenceId;
  }

  public Optional<Protos.ResourceProviderID> getProviderId() {
    return providerId;
  }

  public Optional<Protos.Resource.DiskInfo.Source> getDiskSource() {
    return diskSource;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }
}
