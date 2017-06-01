package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.ResourceCollectionUtils;
import com.mesosphere.sdk.specification.PodSpec;
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
 * Created by gabriel on 5/31/17.
 */
public class ExecutorResourceMapper {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PodSpec podSpec;
    private final Protos.ExecutorInfo executorInfo;
    private final Collection<VolumeSpec> volumeSpecs;
    private final List<Protos.Resource> resources;
    private final List<Protos.Resource> orphanedResources = new ArrayList<>();
    private final List<OfferEvaluationStage> evaluationStages;

    static class VolumeLabels {
        private final VolumeSpec volumeSpec;
        private final String resourceId;
        private final String persistenceId;

        private VolumeLabels(VolumeSpec volumeSpec, String resourceId, String persistenceId) {
            this.volumeSpec = volumeSpec;
            this.resourceId = resourceId;
            this.persistenceId = persistenceId;
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }
    }

    public ExecutorResourceMapper(PodSpec podSpec, Protos.ExecutorInfo executorInfo) {
        this.podSpec = podSpec;
        this.executorInfo = executorInfo;
        this.volumeSpecs = podSpec.getVolumes();
        this.resources = executorInfo.getResourcesList();
        this.evaluationStages = getEvaluationStagesInternal();
    }

    public List<Protos.Resource> getOrphanedResources() {
        return orphanedResources;
    }

    public Collection<? extends OfferEvaluationStage> getEvaluationStages() {
        return evaluationStages;
    }

    private List<OfferEvaluationStage> getEvaluationStagesInternal() {
        List<VolumeSpec> remainingVolumeSpecs = new ArrayList<>();
        remainingVolumeSpecs.addAll(volumeSpecs);

        List<VolumeLabels> matchingVolumes = new ArrayList<>();
        for (Protos.Resource resource : resources) {
            Optional<VolumeLabels> matchingVolume = findMatchingDiskSpec(resource, remainingVolumeSpecs);
            if (matchingVolume.isPresent()) {
                if (!remainingVolumeSpecs.remove(matchingVolume.get().volumeSpec)) {
                    throw new IllegalStateException(
                            String.format(
                                    "Didn't find %s in %s",
                                    matchingVolume.get().volumeSpec, remainingVolumeSpecs));
                }
                matchingVolumes.add(matchingVolume.get());
            } else {
                orphanedResources.add(resource);
            }
        }

        List<OfferEvaluationStage> stages = new ArrayList<>();

        if (!orphanedResources.isEmpty()) {
            logger.info("Orphaned task resources no longer in VolumeSpec: {}",
                    orphanedResources.stream().map(r -> TextFormat.shortDebugString(r)).collect(Collectors.toList()));
        }

        if (!matchingVolumes.isEmpty()) {
            logger.info("Matching task/TaskSpec resources: {}", matchingVolumes);
            for (VolumeLabels volumeLabels : matchingVolumes) {
                VolumeEvaluationStage volumeEvaluationStage = new VolumeEvaluationStage(
                        volumeLabels.volumeSpec,
                        null,
                        Optional.of(volumeLabels.resourceId),
                        Optional.of(volumeLabels.persistenceId));
                stages.add(volumeEvaluationStage);
            }
        }

        // these are resourcespecs which weren't found in the taskinfo resources. likely need new reservations
        if (!remainingVolumeSpecs.isEmpty()) {
            logger.info("Missing VolumeSpec resources not found in executor: {}", remainingVolumeSpecs);
            for (VolumeSpec missingVolume : remainingVolumeSpecs) {
                VolumeEvaluationStage volumeEvaluationStage = new VolumeEvaluationStage(
                        missingVolume,
                        null,
                        Optional.empty(),
                        Optional.empty());
                stages.add(volumeEvaluationStage);
            }
        }

        return stages;
    }

    private Optional<VolumeLabels> findMatchingDiskSpec(Protos.Resource resource, Collection<VolumeSpec> volumeSpecs) {
        for (VolumeSpec volumeSpec : volumeSpecs) {
            if (resource.getDisk().getVolume().getContainerPath().equals(volumeSpec.getContainerPath())) {
                Optional<String> resourceId = ResourceCollectionUtils.getResourceId(resource);
                if (!resourceId.isPresent()) {
                    logger.error("Failed to find resource ID for resource: {}", resource);
                    continue;
                }

                return Optional.of(new VolumeLabels(
                        volumeSpec,
                        resourceId.get(),
                        resource.getDisk().getPersistence().getId()));
            }
        }

        return Optional.empty();
    }
}
