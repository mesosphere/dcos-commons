package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles cross-referencing a preexisting {@link Protos.ExecutorInfo}'s current {@link Protos.Resource}s against a set
 * of expected {@link VolumeSpec}s for that Executor.
 */
public class ExecutorResourceMapper {

    private final Logger logger;
    private final Collection<ResourceSpec> resourceSpecs;
    private final Collection<VolumeSpec> volumeSpecs;
    private final Collection<Protos.Resource> executorResources;
    private final Optional<String> resourceNamespace;
    private final List<Protos.Resource> orphanedResources = new ArrayList<>();
    private final List<OfferEvaluationStage> evaluationStages;

    public ExecutorResourceMapper(
            PodSpec podSpec,
            Collection<ResourceSpec> resourceSpecs,
            Collection<Protos.Resource> executorResources,
            Optional<String> resourceNamespace,
            boolean useDefaultExecutor) {
        this.logger = LoggingUtils.getLogger(getClass(), resourceNamespace);
        this.volumeSpecs = podSpec.getVolumes();
        this.resourceSpecs = resourceSpecs;
        this.executorResources = executorResources;
        this.resourceNamespace = resourceNamespace;
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
        remainingResourceSpecs.addAll(resourceSpecs);

        List<ResourceLabels> matchingResources = new ArrayList<>();
        for (Protos.Resource resource : executorResources) {
            Optional<ResourceLabels> matchingResource;
            if (resource.getName().equals(Constants.DISK_RESOURCE_TYPE) && resource.hasDisk()) {
                matchingResource = findMatchingDiskSpec(resource, remainingResourceSpecs);
            } else {
                matchingResource = findMatchingResourceSpec(resource, remainingResourceSpecs);
            }

            if (matchingResource.isPresent()) {
                if (!remainingResourceSpecs.remove(matchingResource.get().getOriginal())) {
                    throw new IllegalStateException(
                            String.format(
                                    "Didn't find %s in %s",
                                    matchingResource.get().getOriginal(), remainingResourceSpecs));
                }
                matchingResources.add(matchingResource.get());
            } else {
                logger.warn("Failed to find match for resource: {}", TextFormat.shortDebugString(resource));
                if (resource.hasDisk()) {
                    orphanedResources.add(resource);
                }
            }
        }

        List<OfferEvaluationStage> stages = new ArrayList<>();

        if (!orphanedResources.isEmpty()) {
            logger.info("Orphaned executor resources no longer in executor: {}",
                    orphanedResources.stream().map(r -> TextFormat.shortDebugString(r)).collect(Collectors.toList()));
        }

        if (!matchingResources.isEmpty()) {
            logger.info("Matching executor resources: {}", matchingResources);
            for (ResourceLabels resourceLabels : matchingResources) {
                stages.add(newUpdateEvaluationStage(resourceLabels));
            }
        }

        if (!remainingResourceSpecs.isEmpty()) {
            logger.info("Missing resources not found in executor: {}", remainingResourceSpecs);
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
                    logger.error("Failed to find resource ID for resource: {}", executorResource);
                    continue;
                }

                double diskSize = executorResource.getScalar().getValue();
                VolumeSpec updatedSpec = OfferEvaluationUtils.updateVolumeSpec((VolumeSpec) resourceSpec, diskSize);

                return Optional.of(new ResourceLabels(
                        resourceSpec,
                        updatedSpec,
                        resourceId.get(),
                        Optional.of(executorResource.getDisk().getPersistence().getId()),
                        ResourceUtils.getSourceRoot(executorResource)));
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
                    logger.error("Failed to find resource ID for resource: {}", taskResource);
                    continue;
                }

                return Optional.of(new ResourceLabels(resourceSpec, resourceId.get()));
            }
        }
        return Optional.empty();
    }

    private OfferEvaluationStage newUpdateEvaluationStage(ResourceLabels resourceLabels) {
        ResourceSpec resourceSpec = resourceLabels.getUpdated();
        Optional<String> resourceId = Optional.of(resourceLabels.getResourceId());

        if (resourceSpec instanceof VolumeSpec) {
            return VolumeEvaluationStage.getExisting(
                    (VolumeSpec) resourceSpec,
                    Optional.empty(),
                    resourceId,
                    resourceNamespace,
                    resourceLabels.getPersistenceId(),
                    resourceLabels.getSourceRoot());
        } else {
            return new ResourceEvaluationStage(resourceSpec, Optional.empty(), resourceId, resourceNamespace);
        }
    }

    private OfferEvaluationStage newCreateEvaluationStage(ResourceSpec resourceSpec) {
        if (resourceSpec instanceof VolumeSpec) {
            return VolumeEvaluationStage.getNew(
                    (VolumeSpec) resourceSpec, Optional.empty(), resourceNamespace);
        } else {
            return new ResourceEvaluationStage(resourceSpec, Optional.empty(), Optional.empty(), resourceNamespace);
        }
    }
}
