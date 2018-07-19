package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Optional;

class ResourceMapperUtils {

    private static final Logger LOGGER = LoggingUtils.getLogger(ResourceMapperUtils.class);

    private ResourceMapperUtils() {
        // Do not instantiate
    }

    static Optional<ResourceLabels> findMatchingDiskSpec(
            Protos.Resource taskResource,
            Collection<ResourceSpec> resourceSpecs,
            Optional<String> resourceNamespace
    ) {
        return resourceSpecs
                .stream()
                .filter(resourceSpec -> {
                    if (!(resourceSpec instanceof VolumeSpec) ||
                            !taskResource
                                    .getDisk()
                                    .getVolume()
                                    .getContainerPath()
                                    .equals(((VolumeSpec) resourceSpec).getContainerPath())) {
                        return false;
                    }
                    if (!ResourceUtils.getResourceId(taskResource).isPresent()) {
                        LOGGER.error("Failed to find resource ID for resource: {}", taskResource);
                        return false;
                    }
                    return true;
                })
                .findFirst()
                .map(resourceSpec -> new ResourceLabels(
                        resourceSpec,
                        OfferEvaluationUtils.updateVolumeSpec(
                                (VolumeSpec) resourceSpec, taskResource.getScalar().getValue()
                        ),
                        ResourceUtils.getResourceId(taskResource).get(),
                        getNamespaceLabel(ResourceUtils.getNamespace(taskResource), resourceNamespace),
                        Optional.of(taskResource.getDisk().getPersistence().getId()),
                        ResourceUtils.getSourceRoot(taskResource))
                );
    }

    static Optional<ResourceLabels> findMatchingResourceSpec(
            Protos.Resource taskResource,
            Collection<ResourceSpec> resourceSpecs,
            Optional<String> resourceNamespace
    ) {
        return resourceSpecs
                .stream()
                .filter(resourceSpec -> {
                    if (!resourceSpec.getName().equals(taskResource.getName())) {
                        return false;
                    }
                    if (!ResourceUtils.getResourceId(taskResource).isPresent()) {
                        LOGGER.error("Failed to find resource ID for resource: {}", taskResource);
                        return false;
                    }
                    return true;
                })
                .findFirst()
                .map(resourceSpec -> new ResourceLabels(
                        resourceSpec,
                        ResourceUtils.getResourceId(taskResource).get(),
                        getNamespaceLabel(ResourceUtils.getNamespace(taskResource), resourceNamespace))
                );
    }

    /**
     * @param taskResourceNamespace This is the namespace label from the Mesos
     * @return If the taskResourceNamespace is non-empty, it MUST match with the namespace the scheduler is in.
     * If the taskResourceNamespace is empty, we should NOT add a label now.
     * This is applicable only in the "UPDATE" flow. During creating of new resources, we use the Scheduler namespace.
     */
    static Optional<String> getNamespaceLabel(
            Optional<String> taskResourceNamespace,
            Optional<String> resourceNamespace
    ) {
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
