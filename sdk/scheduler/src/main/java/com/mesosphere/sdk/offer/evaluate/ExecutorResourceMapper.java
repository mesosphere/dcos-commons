package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles cross-referencing a preexisting {@link Protos.ExecutorInfo}'s current {@link Protos.Resource}s against a set
 * of expected {@link VolumeSpec}s for that Executor.
 */
public class ExecutorResourceMapper extends AbstractResourceMapper {

    private final Collection<ResourceSpec> resourceSpecs;
    private final Collection<VolumeSpec> volumeSpecs;
    private final Collection<Protos.Resource> executorResources;
    private final Optional<String> resourceNamespace;
    private final List<Protos.Resource> orphanedResources = new ArrayList<>();
    private final List<OfferEvaluationStage> evaluationStages;

    ExecutorResourceMapper(
            PodSpec podSpec,
            Collection<ResourceSpec> resourceSpecs,
            Collection<Protos.Resource> executorResources,
            Optional<String> resourceNamespace) {
        super(resourceNamespace);
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
                    orphanedResources.stream().map(TextFormat::shortDebugString).collect(Collectors.toList()));
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

    private OfferEvaluationStage newUpdateEvaluationStage(ResourceLabels resourceLabels) {
        ResourceSpec resourceSpec = resourceLabels.getUpdated();
        Optional<String> resourceId = Optional.of(resourceLabels.getResourceId());

        if (resourceSpec instanceof VolumeSpec) {
            return VolumeEvaluationStage.getExisting(
                    (VolumeSpec) resourceSpec,
                    Optional.empty(),
                    resourceId,
                    resourceLabels.getResourceNamespace(),
                    resourceLabels.getPersistenceId(),
                    resourceLabels.getSourceRoot());
        } else {
            return new ResourceEvaluationStage(
                    resourceSpec,
                    Optional.empty(),
                    resourceId,
                    resourceLabels.getResourceNamespace()
            );
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
