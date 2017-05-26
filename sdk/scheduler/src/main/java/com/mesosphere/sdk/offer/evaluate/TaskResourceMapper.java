package com.mesosphere.sdk.offer.evaluate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.RangeAlgorithms;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;

/**
 * Handles cross-referencing a preexisting {@link Protos.TaskInfo}'s current {@link Protos.Resource}s against a set
 * of expected {@link ResourceSpec}s for that task.
 */
class TaskResourceMapper {
    private static final Logger logger = LoggerFactory.getLogger(TaskResourceMapper.class);

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

    private final String taskSpecName;
    private final Collection<ResourceSpec> resourceSpecs;
    private final Collection<Protos.Resource> resources;
    private final Map<String, String> taskEnv;

    public TaskResourceMapper(TaskSpec taskSpec, Protos.TaskInfo taskInfo) {
        this.taskSpecName = taskSpec.getName();
        this.resourceSpecs = new ArrayList<>();
        this.resourceSpecs.addAll(taskSpec.getResourceSet().getResources());
        this.resourceSpecs.addAll(taskSpec.getResourceSet().getVolumes());
        this.resources = taskInfo.getResourcesList();
        this.taskEnv = EnvUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
    }

    public List<OfferEvaluationStage> getEvaluationStages() {
        List<ResourceSpec> remainingResourceSpecs = new ArrayList<>();
        remainingResourceSpecs.addAll(resourceSpecs);

        // these are resourcespecs which were matched with taskinfo resources. may need updates
        List<ResourceLabels> matchingResources = new ArrayList<>();
        // these are taskinfo resources which weren't found in the resourcespecs. likely need dereservations
        List<Protos.Resource> orphanedResources = new ArrayList<>();
        for (Protos.Resource taskResource : resources) {
            Optional<ResourceLabels> matchingResource;
            switch (taskResource.getName()) {
            case Constants.DISK_RESOURCE_TYPE:
                matchingResource = findMatchingDiskSpec(taskResource, remainingResourceSpecs);
                break;
            case Constants.PORTS_RESOURCE_TYPE:
                matchingResource = findMatchingPortSpec(taskResource, remainingResourceSpecs, taskEnv);
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
            // TODO(nickbp) add a stage that unreserves/destroys...
            logger.info("Orphaned task resources no longer in TaskSpec: {}",
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
                return Optional.of(new ResourceLabels(
                        resourceSpec,
                        ResourceUtils.getResourceId(taskResource),
                        Optional.of(taskResource.getDisk().getPersistence().getId())));
            }
        }
        return Optional.empty();
    }

    private Optional<ResourceLabels> findMatchingPortSpec(
            Protos.Resource taskResource, Collection<ResourceSpec> resourceSpecs, Map<String, String> taskEnv) {
        for (ResourceSpec resourceSpec : resourceSpecs) {
            if (!(resourceSpec instanceof PortSpec)) {
                continue;
            }
            PortSpec portSpec = (PortSpec) resourceSpec;
            if (portSpec.getPort() == 0) {
                // For dynamic ports, we need to detect the port that we actually used.
                // TODO(nickbp): Once we're storing port name as a label in the task resource, search for that instead.
                //               We should then only check env as a fallback when the label isn't present.
                String portEnvVal = taskEnv.get(PortEvaluationStage.getPortEnvironmentVariable(portSpec));
                if (portEnvVal != null
                        && RangeAlgorithms.isInAny(
                                taskResource.getRanges().getRangeList(),
                                Integer.parseInt(portEnvVal))) {
                    // The advertised port value is present in this resource. Resource must match!
                    return Optional.of(new ResourceLabels(resourceSpec, ResourceUtils.getResourceId(taskResource)));
                }
            } else {
                // For fixed ports, we can just check for a resource whose ranges include that port.
                if (RangeAlgorithms.isInAny(taskResource.getRanges().getRangeList(), portSpec.getPort())) {
                    return Optional.of(new ResourceLabels(resourceSpec, ResourceUtils.getResourceId(taskResource)));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ResourceLabels> findMatchingResourceSpec(
            Protos.Resource taskResource, Collection<ResourceSpec> resourceSpecs) {
        for (ResourceSpec resourceSpec : resourceSpecs) {
            if (resourceSpec.getName().equals(taskResource.getName())) {
                return Optional.of(new ResourceLabels(resourceSpec, ResourceUtils.getResourceId(taskResource)));
            }
        }
        return Optional.empty();
    }

    private static OfferEvaluationStage newUpdateEvaluationStage(String taskSpecName, ResourceLabels resourceLabels) {
        return toEvaluationStage(taskSpecName, resourceLabels.resourceSpec, Optional.of(resourceLabels.resourceId),
                resourceLabels.persistenceId);
    }

    private static OfferEvaluationStage newCreateEvaluationStage(String taskSpecName, ResourceSpec resourceSpec) {
        return toEvaluationStage(taskSpecName, resourceSpec, Optional.empty(), Optional.empty());
    }

    private static OfferEvaluationStage toEvaluationStage(
            String taskSpecName,
            ResourceSpec resourceSpec,
            Optional<String> resourceId,
            Optional<String> persistenceId) {
        if (resourceSpec instanceof NamedVIPSpec) {
            return new NamedVIPEvaluationStage((NamedVIPSpec) resourceSpec, taskSpecName, resourceId);
        } else if (resourceSpec instanceof PortSpec) {
            return new PortEvaluationStage((PortSpec) resourceSpec, taskSpecName, resourceId);
        } else if (resourceSpec instanceof VolumeSpec) {
            return new VolumeEvaluationStage((VolumeSpec) resourceSpec, taskSpecName, resourceId, persistenceId);
        } else {
            return new ResourceEvaluationStage(resourceSpec, resourceId, taskSpecName);
        }
    }
}
