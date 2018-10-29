package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.RangeUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.specification.*;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles cross-referencing a preexisting {@link Protos.TaskInfo}'s current {@link Protos.Resource}s against a set
 * of expected {@link ResourceSpec}s for that task.
 */
class TaskResourceMapper {

    private final Logger logger;
    private final Optional<String> resourceNamespace;
    private final String taskSpecName;
    private final List<Protos.Resource> orphanedResources = new ArrayList<>();
    private final Collection<ResourceSpec> resourceSpecs;
    private final TaskPortLookup taskPortFinder;
    private final Collection<Protos.Resource> resources;

    private final List<OfferEvaluationStage> evaluationStages;

    TaskResourceMapper(
            TaskSpec taskSpec,
            Protos.TaskInfo taskInfo,
            Optional<String> resourceNamespace) {
        logger = LoggingUtils.getLogger(getClass(), resourceNamespace);
        this.resourceNamespace = resourceNamespace;
        this.taskSpecName = taskSpec.getName();
        this.resourceSpecs = new ArrayList<>();
        this.resourceSpecs.addAll(taskSpec.getResourceSet().getResources());
        this.resourceSpecs.addAll(taskSpec.getResourceSet().getVolumes());
        this.taskPortFinder = new TaskPortLookup(taskInfo);
        this.resources = taskInfo.getResourcesList();

        // ONLY call this AFTER initializing all members above:
        this.evaluationStages = getEvaluationStagesInternal();
    }

    public List<Protos.Resource> getOrphanedResources() {
        return orphanedResources;
    }

    public List<OfferEvaluationStage> getEvaluationStages() {
        return evaluationStages;
    }

    private List<OfferEvaluationStage> getEvaluationStagesInternal() {
        // These are taskinfo resources which weren't found in the resourcespecs. Likely need dereservations.
        List<ResourceSpec> remainingResourceSpecs = new ArrayList<>(resourceSpecs);

        // These are resourcespecs which were matched with taskinfo resources. May need updates.
        List<ResourceLabels> matchingResources = new ArrayList<>();
        for (Protos.Resource taskResource : resources) {
            Optional<ResourceLabels> matchingResource;
            switch (taskResource.getName()) {
                case Constants.DISK_RESOURCE_TYPE:
                    matchingResource = ResourceMapperUtils.findMatchingDiskSpec(
                            taskResource,
                            remainingResourceSpecs,
                            resourceNamespace);
                    break;
                case Constants.PORTS_RESOURCE_TYPE:
                    matchingResource = findMatchingPortSpec(taskResource, remainingResourceSpecs);
                    break;
                default:
                    matchingResource = ResourceMapperUtils.findMatchingResourceSpec(
                            taskResource,
                            remainingResourceSpecs,
                            resourceNamespace);
                    break;
            }
            if (matchingResource.isPresent()) {
                if (!remainingResourceSpecs.remove(matchingResource.get().getOriginal())) {
                    throw new IllegalStateException(String.format("Didn't find %s in %s",
                            matchingResource.get().getOriginal(), remainingResourceSpecs));
                }
                matchingResources.add(matchingResource.get());
            } else {
                orphanedResources.add(taskResource);
            }
        }

        List<OfferEvaluationStage> stages = new ArrayList<>();

        if (!orphanedResources.isEmpty()) {
            logger.info("Unreserving orphaned task resources no longer in TaskSpec: {}",
                    orphanedResources.stream().map(TextFormat::shortDebugString).collect(Collectors.toList()));
        }

        if (!matchingResources.isEmpty()) {
            logger.info("Matching task/TaskSpec resources: {}", matchingResources);
            for (ResourceLabels resourceLabels : matchingResources) {
                stages.add(newUpdateEvaluationStage(taskSpecName, resourceLabels));
            }
        }

        if (!remainingResourceSpecs.isEmpty()) {
            logger.info("Missing TaskSpec resources not found in task: {}", remainingResourceSpecs);
            for (ResourceSpec missingResource : remainingResourceSpecs) {
                stages.add(newCreateEvaluationStage(taskSpecName, missingResource));
            }
        }
        return stages;
    }

    private Optional<ResourceLabels> findMatchingPortSpec(
            Protos.Resource taskResource, Collection<ResourceSpec> resourceSpecs) {
        Protos.Value.Ranges ranges = taskResource.getRanges();
        boolean hasMultiplePorts = ranges.getRangeCount() != 1
                || ranges.getRange(0).getEnd() - ranges.getRange(0).getBegin() != 0;

        if (hasMultiplePorts) {
            return Optional.empty();
        }

        Optional<String> resourceId = ResourceUtils.getResourceId(taskResource);
        if (!resourceId.isPresent()) {
            logger.error("Failed to find resource ID for resource: {}", taskResource);
            return Optional.empty();
        }

        for (ResourceSpec resourceSpec : resourceSpecs) {
            if (!(resourceSpec instanceof PortSpec)) {
                continue;
            }
            PortSpec portSpec = (PortSpec) resourceSpec;
            if (portSpec.getPort() == 0) {
                // For dynamic ports, we need to detect the port value that we had selected.
                return taskPortFinder
                        .getPriorPort(portSpec)
                        .filter(priorTaskPort -> RangeUtils.isInAny(ranges.getRangeList(), priorTaskPort))
                        .map(ignored -> new ResourceLabels(
                                resourceSpec,
                                resourceId.get(),
                                ResourceMapperUtils.getNamespaceLabel(
                                        ResourceUtils.getNamespace(taskResource),
                                        resourceNamespace
                                )
                        ));
            } else if (RangeUtils.isInAny(ranges.getRangeList(), portSpec.getPort())) {
                // For fixed ports, we can just check for a resource whose ranges include that port.
                return Optional.of(new ResourceLabels(
                        resourceSpec,
                        resourceId.get(),
                        ResourceMapperUtils.getNamespaceLabel(
                                ResourceUtils.getNamespace(taskResource),
                                resourceNamespace)
                        )
                );
            }
        }
        return Optional.empty();
    }

    private OfferEvaluationStage newUpdateEvaluationStage(String taskSpecName, ResourceLabels resourceLabels) {
        return toEvaluationStage(
                taskSpecName,
                resourceLabels.getUpdated(),
                Optional.of(resourceLabels.getResourceId()),
                resourceLabels.getResourceNamespace(),
                resourceLabels.getPersistenceId(),
                resourceLabels.getProviderId(),
                resourceLabels.getDiskSource());
    }

    private OfferEvaluationStage newCreateEvaluationStage(String taskSpecName, ResourceSpec resourceSpec) {
        return toEvaluationStage(
                taskSpecName,
                resourceSpec,
                Optional.empty(),
                resourceNamespace,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private OfferEvaluationStage toEvaluationStage(
            String taskSpecName,
            ResourceSpec resourceSpec,
            Optional<String> resourceId,
            Optional<String> resourceNamespace,
            Optional<String> persistenceId,
            Optional<Protos.ResourceProviderID> providerId,
            Optional<Protos.Resource.DiskInfo.Source> diskSource) {
        if (resourceSpec instanceof NamedVIPSpec) {
            return new NamedVIPEvaluationStage(
                    (NamedVIPSpec) resourceSpec, taskSpecName, resourceId, resourceNamespace);
        } else if (resourceSpec instanceof PortSpec) {
            return new PortEvaluationStage((PortSpec) resourceSpec, taskSpecName, resourceId, resourceNamespace);
        } else if (resourceSpec instanceof VolumeSpec) {
            return VolumeEvaluationStage.getExisting(
                    (VolumeSpec) resourceSpec,
                    Optional.of(taskSpecName),
                    resourceId,
                    resourceNamespace,
                    persistenceId,
                    providerId,
                    diskSource);
        } else {
            return new ResourceEvaluationStage(resourceSpec, Optional.of(taskSpecName), resourceId, resourceNamespace);
        }
    }
}
