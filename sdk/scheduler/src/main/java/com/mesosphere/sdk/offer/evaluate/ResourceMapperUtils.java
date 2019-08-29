package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.VolumeSpec;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Optional;

@SuppressWarnings({
    "checkstyle:FinalClass",
    "checkstyle:MultipleStringLiterals"
})
class ResourceMapperUtils {

  private static final Logger LOGGER = LoggingUtils.getLogger(ResourceMapperUtils.class);

  private ResourceMapperUtils() {
    // Do not instantiate
  }

  static Optional<ResourceLabels> findMatchingDiskSpec(
      Protos.Resource taskResource,
      Collection<ResourceSpec> resourceSpecs)
  {
    if (!ResourceUtils.getResourceId(taskResource).isPresent()) {
      LOGGER.error("Failed to find resource ID for resource: {}", taskResource);
      return Optional.empty();
    }
    return resourceSpecs.stream()
        .filter(resourceSpec -> resourceSpec instanceof VolumeSpec
            && taskResource
            .getDisk()
            .getVolume()
            .getContainerPath()
            .equals(((VolumeSpec) resourceSpec).getContainerPath()))
        .findFirst()
        .map(resourceSpec -> new ResourceLabels(
            resourceSpec,
            ((VolumeSpec) resourceSpec).withDiskSize(taskResource.getScalar().getValue()),
            ResourceUtils.getResourceId(taskResource).get(),
            ResourceUtils.getPersistenceId(taskResource),
            ResourceUtils.getProviderId(taskResource),
            ResourceUtils.getDiskSource(taskResource)));
  }

  static Optional<ResourceLabels> findMatchingResourceSpec(
      Protos.Resource taskResource,
      Collection<ResourceSpec> resourceSpecs)
  {
    if (!ResourceUtils.getResourceId(taskResource).isPresent()) {
      LOGGER.error("Failed to find resource ID for resource: {}", taskResource);
      return Optional.empty();
    }
    return resourceSpecs.stream()
        .filter(resourceSpec -> resourceSpec.getName().equals(taskResource.getName()))
        .findFirst()
        .map(resourceSpec -> new ResourceLabels(
            resourceSpec,
            ResourceUtils.getResourceId(taskResource).get(),
            getNamespaceLabel(ResourceUtils.getNamespace(taskResource), Optional.empty())));
  }

  /**
   * @param taskResourceNamespace This is the namespace label from the Mesos
   * @return If the taskResourceNamespace is non-empty, it MUST match with the namespace the scheduler is in.
   * If the taskResourceNamespace is empty, we should NOT add a label now.
   * This is applicable only in the "UPDATE" flow. During creating of new resources, we use the Scheduler namespace.
   */
  static Optional<String> getNamespaceLabel(
      Optional<String> taskResourceNamespace,
      Optional<String> resourceNamespace)
  {
    return taskResourceNamespace.flatMap(x -> {
      if (!resourceNamespace.isPresent() || !resourceNamespace.get().equals(x)) {
        LOGGER.error("Resource has [{}] namespace label but scheduler is in [{}] namespace",
            x, resourceNamespace);
        return Optional.empty();
      } else {
        return taskResourceNamespace;
      }
    });
  }
}
