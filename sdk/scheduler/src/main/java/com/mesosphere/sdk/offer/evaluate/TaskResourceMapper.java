package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.RangeUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.specification.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles cross-referencing a preexisting {@link Protos.TaskInfo}'s current {@link Protos.Resource}s against a set
 * of expected {@link ResourceSpec}s for that task.
 */
class TaskResourceMapper {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final List<Protos.Resource> orphanedResources = new ArrayList<>();
    private final List<OfferEvaluationStage> evaluationStages;
    private final String taskSpecName;
    private final Collection<ResourceSpec> resourceSpecs;
    private final TaskPortLookup taskPortFinder;
    private final Collection<Protos.Resource> resources;
    private final boolean useDefaultExecutor;

    /**
     * Pairs a {@link ResourceSpec} definition with an existing task's labels associated with that resource.
     */
    static class ResourceLabels {
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

    public TaskResourceMapper(TaskSpec taskSpec, Protos.TaskInfo taskInfo, boolean useDefaultExecutor) {
        this.taskSpecName = taskSpec.getName();
        this.resourceSpecs = new ArrayList<>();
        this.resourceSpecs.addAll(taskSpec.getResourceSet().getResources());
        this.resourceSpecs.addAll(taskSpec.getResourceSet().getVolumes());
        this.taskPortFinder = new TaskPortLookup(taskInfo);
        this.resources = taskInfo.getResourcesList();
        this.evaluationStages = getEvaluationStagesInternal();
        this.useDefaultExecutor = useDefaultExecutor;
    }

    public List<Protos.Resource> getOrphanedResources() {
        return orphanedResources;
    }

    public List<OfferEvaluationStage> getEvaluationStages() {
        return evaluationStages;
    }

    private List<OfferEvaluationStage> getEvaluationStagesInternal() {
        List<ResourceSpec> remainingResourceSpecs = new ArrayList<>();
        remainingResourceSpecs.addAll(resourceSpecs);

        // these are resourcespecs which were matched with taskinfo resources. may need updates
        List<ResourceLabels> matchingResources = new ArrayList<>();
        // these are taskinfo resources which weren't found in the resourcespecs. likely need dereservations
        for (Protos.Resource taskResource : resources) {
            Optional<ResourceLabels> matchingResource;
            switch (taskResource.getName()) {
                case Constants.DISK_RESOURCE_TYPE:
                    matchingResource = findMatchingDiskSpec(taskResource, remainingResourceSpecs);
                    break;
                case Constants.PORTS_RESOURCE_TYPE:
                    matchingResource = findMatchingPortSpec(taskResource, remainingResourceSpecs);
                    break;
                default:
                    matchingResource = findMatchingResourceSpec(taskResource, remainingResourceSpecs);
                    break;
            }
            if (matchingResource.isPresent()) {
                if (!remainingResourceSpecs.remove(matchingResource.get().resourceSpec)) {
                    throw new IllegalStateException(String.format("Didn't find %s in %s",
                            matchingResource.get().resourceSpec, remainingResourceSpecs));
                }
                matchingResources.add(matchingResource.get());
            } else {
                orphanedResources.add(taskResource);
            }
        }

        List<OfferEvaluationStage> stages = new ArrayList<>();

        if (!orphanedResources.isEmpty()) {
            logger.info("Unreserving orphaned task resources no longer in TaskSpec: {}",
                    orphanedResources.stream().map(r -> TextFormat.shortDebugString(r)).collect(Collectors.toList()));
        }

        if (!matchingResources.isEmpty()) {
            logger.info("Matching task/TaskSpec resources: {}", matchingResources);
            for (ResourceLabels resourceLabels : matchingResources) {
                stages.add(newUpdateEvaluationStage(taskSpecName, resourceLabels));
            }
        }

        // these are resourcespecs which weren't found in the taskinfo resources. likely need new reservations
        if (!remainingResourceSpecs.isEmpty()) {
            logger.info("Missing TaskSpec resources not found in task: {}", remainingResourceSpecs);
            for (ResourceSpec missingResource : remainingResourceSpecs) {
                stages.add(newCreateEvaluationStage(taskSpecName, missingResource));
            }
        }
        return stages;
    }

    private Optional<ResourceLabels> findMatchingDiskSpec(
            Protos.Resource taskResource, Collection<ResourceSpec> resourceSpecs) {
        for (ResourceSpec resourceSpec : resourceSpecs) {
            if (!(resourceSpec instanceof VolumeSpec)) {
                continue;
            }

            if (taskResource.getDisk().getVolume().getContainerPath().equals(
                    ((VolumeSpec) resourceSpec).getContainerPath())) {
                Optional<String> resourceId = ResourceUtils.getResourceId(taskResource);
                if (!resourceId.isPresent()) {
                    logger.error("Failed to find resource ID for resource: {}", taskResource);
                    continue;
                }

                return Optional.of(new ResourceLabels(
                        resourceSpec,
                        resourceId.get(),
                        Optional.of(taskResource.getDisk().getPersistence().getId())));
            }
        }

        return Optional.empty();
    }

    private Optional<ResourceLabels> findMatchingPortSpec(
            Protos.Resource taskResource, Collection<ResourceSpec> resourceSpecs) {
        Protos.Value.Ranges ranges = taskResource.getRanges();
        boolean hasMultiplePorts = ranges.getRangeCount() != 1
                || ranges.getRange(0).getEnd() - ranges.getRange(0).getBegin() != 0;

        if (hasMultiplePorts) {
            return Optional.empty();
        }

        for (ResourceSpec resourceSpec : resourceSpecs) {
            if (!(resourceSpec instanceof PortSpec)) {
                continue;
            }
            PortSpec portSpec = (PortSpec) resourceSpec;
            if (portSpec.getPort() == 0) {
                // For dynamic ports, we need to detect the port value that we had selected.
                Optional<Long> priorTaskPort = taskPortFinder.getPriorPort(portSpec);
                if (priorTaskPort.isPresent()
                        && RangeUtils.isInAny(taskResource.getRanges().getRangeList(), priorTaskPort.get())) {

                    // The advertised port value is present in this resource. Resource must match!
                    Optional<String> resourceId = ResourceUtils.getResourceId(taskResource);
                    if (!resourceId.isPresent()) {
                        logger.error("Failed to find resource ID for resource: {}", taskResource);
                        continue;
                    }

                    return Optional.of(new ResourceLabels(resourceSpec, resourceId.get()));
                }
            } else {
                // For fixed ports, we can just check for a resource whose ranges include that port.
                if (RangeUtils.isInAny(taskResource.getRanges().getRangeList(), portSpec.getPort())) {
                    Optional<String> resourceId = ResourceUtils.getResourceId(taskResource);
                    if (!resourceId.isPresent()) {
                        logger.error("Failed to find resource ID for resource: {}", taskResource);
                        continue;
                    }

                    return Optional.of(new ResourceLabels(resourceSpec, resourceId.get()));
                }
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

    private OfferEvaluationStage newUpdateEvaluationStage(String taskSpecName, ResourceLabels resourceLabels) {
        return toEvaluationStage(taskSpecName, resourceLabels.resourceSpec, Optional.of(resourceLabels.resourceId),
                resourceLabels.persistenceId);
    }

    private OfferEvaluationStage newCreateEvaluationStage(String taskSpecName, ResourceSpec resourceSpec) {
        return toEvaluationStage(taskSpecName, resourceSpec, Optional.empty(), Optional.empty());
    }

    private OfferEvaluationStage toEvaluationStage(
            String taskSpecName,
            ResourceSpec resourceSpec,
            Optional<String> resourceId,
            Optional<String> persistenceId) {
        if (resourceSpec instanceof NamedVIPSpec) {
            return new NamedVIPEvaluationStage((NamedVIPSpec) resourceSpec, taskSpecName, resourceId);
        } else if (resourceSpec instanceof PortSpec) {
            return new PortEvaluationStage((PortSpec) resourceSpec, taskSpecName, resourceId);
        } else if (resourceSpec instanceof VolumeSpec) {
            return new VolumeEvaluationStage(
                    (VolumeSpec) resourceSpec, taskSpecName, resourceId, persistenceId, useDefaultExecutor);
        } else {
            return new ResourceEvaluationStage(resourceSpec, resourceId, taskSpecName);
        }
    }
}
