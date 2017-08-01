package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles cross-referencing a preexisting {@link Protos.ExecutorInfo}'s current {@link Protos.Resource}s against a set
 * of expected {@link VolumeSpec}s for that Executor.
 */
public class ExecutorResourceMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorResourceMapper.class);
    private final Protos.ExecutorInfo executorInfo;
    private final Collection<ResourceSpec> resourceSpecs;
    private final Collection<VolumeSpec> volumeSpecs;
    private final List<Protos.Resource> resources;
    private final List<Protos.Resource> orphanedResources = new ArrayList<>();
    private final List<OfferEvaluationStage> evaluationStages;
    private final boolean useDefaultExecutor;

    /**
     * Pairs a {@link ResourceSpec} definition with an existing task's labels associated with that resource.
     */
    private static class ResourceLabels {
        private final ResourceSpec resourceSpec;
        private final String resourceId;
        private final Optional<String> persistenceId;

        private ResourceLabels(ResourceSpec resourceSpec, String resourceId) {
            this(resourceSpec, resourceId, Optional.empty());
        }

        private ResourceLabels(ResourceSpec resourceSpec, String resourceId, Optional<String> persistenceId) {
            this.resourceSpec = resourceSpec;
            this.resourceId = resourceId;
            this.persistenceId = persistenceId;
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }
    }

    public ExecutorResourceMapper(
            PodSpec podSpec,
            Collection<ResourceSpec> resourceSpecs,
            Protos.ExecutorInfo executorInfo,
            boolean useDefaultExecutor) {
        this.executorInfo = executorInfo;
        this.volumeSpecs = podSpec.getVolumes();
        this.resourceSpecs = resourceSpecs;
        this.resources = executorInfo.getResourcesList();
        this.useDefaultExecutor = useDefaultExecutor;
        this.evaluationStages = getEvaluationStagesInternal();
    }

    public List<Protos.Resource> getOrphanedResources() {
        return orphanedResources;
    }

    public Collection<? extends OfferEvaluationStage> getEvaluationStages() {
        return evaluationStages;
    }

    private List<OfferEvaluationStage> getEvaluationStagesInternal() {
        List<ResourceSpec> remainingResourceSpecs = new ArrayList<>();
        remainingResourceSpecs.addAll(volumeSpecs);

        if (useDefaultExecutor && executorInfo.getExecutorId().getValue().isEmpty()) {
            remainingResourceSpecs.addAll(resourceSpecs);
        }

        List<ResourceLabels> matchingResources = new ArrayList<>();
        for (Protos.Resource resource : resources) {
            Optional<ResourceLabels> matchingResource;
            if (resource.getName().equals(Constants.DISK_RESOURCE_TYPE) && resource.hasDisk()) {
                matchingResource = findMatchingDiskSpec(resource, remainingResourceSpecs);
            } else {
                matchingResource = findMatchingResourceSpec(resource, remainingResourceSpecs);
            }

            if (matchingResource.isPresent()) {
                if (!remainingResourceSpecs.remove(matchingResource.get().resourceSpec)) {
                    throw new IllegalStateException(
                            String.format(
                                    "Didn't find %s in %s",
                                    matchingResource.get().resourceSpec, remainingResourceSpecs));
                }
                matchingResources.add(matchingResource.get());
            } else {
                if (resource.hasDisk()) {
                    orphanedResources.add(resource);
                }
            }
        }

        List<OfferEvaluationStage> stages = new ArrayList<>();

        if (!orphanedResources.isEmpty()) {
            LOGGER.info("Orphaned executor resources no longer in executor: {}",
                    orphanedResources.stream().map(r -> TextFormat.shortDebugString(r)).collect(Collectors.toList()));
        }

        if (!matchingResources.isEmpty()) {
            for (ResourceLabels resourceLabels : matchingResources) {
                stages.add(newUpdateEvaluationStage(resourceLabels));
            }
        }

        if (!remainingResourceSpecs.isEmpty()) {
            LOGGER.info("Missing volumes not found in executor: {}", remainingResourceSpecs);
            for (ResourceSpec missingResource : remainingResourceSpecs) {
                stages.add(newCreateEvaluationStage(missingResource));
            }
        }

        return stages;
    }

    private Optional<ResourceLabels> findMatchingDiskSpec(
            Protos.Resource executorResource, Collection<ResourceSpec> resourceSpecs) {
        for (ResourceSpec resourceSpec : resourceSpecs) {
            if (!(resourceSpec instanceof VolumeSpec)) {
                continue;
            }

            if (executorResource.getDisk().getVolume().getContainerPath().equals(
                    ((VolumeSpec) resourceSpec).getContainerPath())) {
                Optional<String> resourceId = ResourceUtils.getResourceId(executorResource);
                if (!resourceId.isPresent()) {
                    LOGGER.error("Failed to find resource ID for resource: {}", executorResource);
                    continue;
                }

                return Optional.of(new ResourceLabels(
                        resourceSpec,
                        resourceId.get(),
                        Optional.of(executorResource.getDisk().getPersistence().getId())));
            }
        }

        return Optional.empty();
    }

    private Optional<ResourceLabels> findMatchingResourceSpec(
            Protos.Resource taskResource, Collection<ResourceSpec> resourceSpecs) {
        for (ResourceSpec resourceSpec : resourceSpecs) {
            if (resourceSpec.getName().equals(taskResource.getName())) {
                Optional<String> resourceId = ResourceUtils.getResourceId(taskResource);
                if (!resourceId.isPresent()) {
                    LOGGER.error("Failed to find resource ID for resource: {}", taskResource);
                    continue;
                }

                return Optional.of(new ResourceLabels(resourceSpec, resourceId.get()));
            }
        }
        return Optional.empty();
    }

    private OfferEvaluationStage newUpdateEvaluationStage(ResourceLabels resourceLabels) {
        return toEvaluationStage(resourceLabels.resourceSpec, Optional.of(resourceLabels.resourceId),
                resourceLabels.persistenceId);
    }

    private OfferEvaluationStage newCreateEvaluationStage(ResourceSpec resourceSpec) {
        return toEvaluationStage(resourceSpec, Optional.empty(), Optional.empty());
    }

    private OfferEvaluationStage toEvaluationStage(
            ResourceSpec resourceSpec,
            Optional<String> resourceId,
            Optional<String> persistenceId) {
        if (resourceSpec instanceof VolumeSpec) {
            return new VolumeEvaluationStage(
                    (VolumeSpec) resourceSpec, null, resourceId, persistenceId, useDefaultExecutor);
        } else {
            return new ResourceEvaluationStage(resourceSpec, resourceId, null);
        }
    }
}
