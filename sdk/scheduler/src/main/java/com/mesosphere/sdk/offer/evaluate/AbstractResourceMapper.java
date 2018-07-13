package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Optional;

abstract class AbstractResourceMapper {
    final Logger LOGGER;
    final Optional<String> resourceNamespace;

    AbstractResourceMapper(Optional<String> resourceNamespace) {
        LOGGER = LoggingUtils.getLogger(getClass(), resourceNamespace);
        this.resourceNamespace = resourceNamespace;
    }

    Optional<ResourceLabels> findMatchingDiskSpec(
            Protos.Resource taskResource,
            Collection<ResourceSpec> resourceSpecs
    ) {
        for (ResourceSpec resourceSpec : resourceSpecs) {
            if (!(resourceSpec instanceof VolumeSpec)) {
                continue;
            }
            if (taskResource.getDisk().getVolume().getContainerPath().equals(
                    ((VolumeSpec) resourceSpec).getContainerPath())) {
                Optional<String> resourceId = ResourceUtils.getResourceId(taskResource);
                if (!resourceId.isPresent()) {
                    LOGGER.error("Failed to find resource ID for resource: {}", taskResource);
                    continue;
                }
                return Optional.of(new ResourceLabels(
                        resourceSpec,
                        OfferEvaluationUtils.updateVolumeSpec(
                                (VolumeSpec) resourceSpec, taskResource.getScalar().getValue()
                        ),
                        resourceId.get(),
                        getNamespaceLabel(ResourceUtils.getNamespace(taskResource)),
                        Optional.of(taskResource.getDisk().getPersistence().getId()),
                        ResourceUtils.getSourceRoot(taskResource)));
            }
        }
        return Optional.empty();
    }

    Optional<ResourceLabels> findMatchingResourceSpec(
            Protos.Resource taskResource,
            Collection<ResourceSpec> resourceSpecs
    ) {
        for (ResourceSpec resourceSpec : resourceSpecs) {
            if (resourceSpec.getName().equals(taskResource.getName())) {
                Optional<String> resourceId = ResourceUtils.getResourceId(taskResource);
                if (!resourceId.isPresent()) {
                    LOGGER.error("Failed to find resource ID for resource: {}", taskResource);
                    continue;
                }
                return Optional.of(new ResourceLabels(
                        resourceSpec,
                        resourceId.get(),
                        getNamespaceLabel(ResourceUtils.getNamespace(taskResource))));
            }
        }
        return Optional.empty();
    }

    /**
     * @param taskResourceNamespace This is the namespace label from the Mesos
     * @return If the taskResourceNamespace is non-empty, it MUST match with the namespace the scheduler is in.
     * If the taskResourceNamespace is empty, we should NOT add a label now.
     * This is applicable only in the "UPDATE" flow. During creating of new resources, we use the Scheduler namespace.
     */
    Optional<String> getNamespaceLabel(
            Optional<String> taskResourceNamespace
    ) {
        return taskResourceNamespace.flatMap(x -> {
            if (!resourceNamespace.isPresent() || !resourceNamespace.get().equals(x)) {
                LOGGER.error("Resource has [{}] namespace label but scheduler is in [{}] namespace",
                        x, this.resourceNamespace);
                return Optional.empty();
            } else {
                return taskResourceNamespace;
            }
        });
    }
}
