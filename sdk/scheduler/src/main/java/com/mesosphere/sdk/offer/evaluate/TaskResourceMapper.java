package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.RangeAlgorithms;
import com.mesosphere.sdk.offer.ResourceCollectionUtils;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
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
    private final Collection<ResourceSpec> executorResources;
    private final Collection<Protos.Resource> resources;
    private final Collection<Protos.Resource> executorShit;
    private final Map<String, String> taskEnv;

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

    public TaskResourceMapper(TaskSpec taskSpec, Protos.TaskInfo taskInfo, Collection<ResourceSpec> executorResources) {
        this.taskSpecName = taskSpec.getName();
        this.resourceSpecs = new ArrayList<>();
        this.resourceSpecs.addAll(taskSpec.getResourceSet().getResources());
        this.resourceSpecs.addAll(taskSpec.getResourceSet().getVolumes());
        this.executorResources = executorResources;
        this.executorShit = taskInfo.getExecutor().getResourcesList();
        this.resources = taskInfo.getResourcesList();
        this.taskEnv = EnvUtils.toMap(taskInfo.getCommand().getEnvironment());
        this.evaluationStages = getEvaluationStagesInternal();
    }

    public List<Protos.Resource> getOrphanedResources() {
        return orphanedResources;
    }

    public List<OfferEvaluationStage> getEvaluationStages() {
        return evaluationStages;
    }

    private List<ResourceLabels> getMatchingResources(
            Collection<ResourceSpec> newResourceSpecs, Collection<Protos.Resource> existingResources) {
        List<ResourceLabels> matchingResources = new ArrayList<>();

        // these are resources which weren't found in the resourcespecs. likely need dereservations
        for (Protos.Resource resource : existingResources) {
            Optional<ResourceLabels> matchingResource;
            switch (resource.getName()) {
                case Constants.DISK_RESOURCE_TYPE:
                    matchingResource = findMatchingDiskSpec(resource, newResourceSpecs);
                    break;
                case Constants.PORTS_RESOURCE_TYPE:
                    matchingResource = findMatchingPortSpec(resource, newResourceSpecs, taskEnv);
                    break;
                default:
                    matchingResource = findMatchingResourceSpec(resource, newResourceSpecs);
                    break;
            }
            if (matchingResource.isPresent()) {
                if (!newResourceSpecs.remove(matchingResource.get().resourceSpec)) {
                    throw new IllegalStateException(String.format("Didn't find %s in %s",
                            matchingResource.get().resourceSpec, newResourceSpecs));
                }
                matchingResources.add(matchingResource.get());
            } else {
                orphanedResources.add(resource);
            }
        }

        return matchingResources;
    }

    private List<OfferEvaluationStage> getEvaluationStagesInternal() {
        List<ResourceSpec> remainingResourceSpecs = new ArrayList<>();
        remainingResourceSpecs.addAll(resourceSpecs);

        // these are resourcespecs which were matched with task/executor resources. may need updates
        List<ResourceLabels> matchingTaskResources = getMatchingResources(resourceSpecs, resources);
        List<ResourceLabels> matchingExecutorResources = getMatchingResources(executorResources, executorShit);

        List<OfferEvaluationStage> stages = new ArrayList<>();

        if (!orphanedResources.isEmpty()) {
            // TODO(nickbp) add a stage that unreserves/destroys...
            logger.info("Orphaned task resources no longer in TaskSpec or executor: {}",
                    orphanedResources.stream().map(r -> TextFormat.shortDebugString(r)).collect(Collectors.toList()));
        }

        if (!matchingTaskResources.isEmpty()) {
            logger.info("Matching task/TaskSpec resources: {}", matchingTaskResources);
            for (ResourceLabels resourceLabels : matchingTaskResources) {
                stages.add(newUpdateEvaluationStage(taskSpecName, resourceLabels));
            }
        }

        // these are resourcespecs which weren't found in the taskinfo resources. likely need new reservations
        if (!resourceSpecs.isEmpty()) {
            logger.info("Missing TaskSpec resources not found in task: {}", remainingResourceSpecs);
            for (ResourceSpec missingResource : resourceSpecs) {
                stages.add(newCreateEvaluationStage(taskSpecName, missingResource));
            }
        }

        if (!matchingExecutorResources.isEmpty()) {
            logger.info("Matching executor resources: {}", matchingExecutorResources);
            for (ResourceLabels resourceLabels : matchingExecutorResources) {
                stages.add(newUpdateEvaluationStage(null, resourceLabels));
            }
        }

        if (!executorResources.isEmpty()) {
            logger.info("Missing executor resources not found in task: {}", executorResources);
            for (ResourceSpec missingResource : executorResources) {
                stages.add(newCreateEvaluationStage(null, missingResource));
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
                Optional<String> resourceId = ResourceCollectionUtils.getResourceId(taskResource);
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
                    Optional<String> resourceId = ResourceCollectionUtils.getResourceId(taskResource);
                    if (!resourceId.isPresent()) {
                        logger.error("Failed to find resource ID for resource: {}", taskResource);
                        continue;
                    }

                    return Optional.of(new ResourceLabels(resourceSpec, resourceId.get()));
                }
            } else {
                // For fixed ports, we can just check for a resource whose ranges include that port.
                if (RangeAlgorithms.isInAny(taskResource.getRanges().getRangeList(), portSpec.getPort())) {
                    Optional<String> resourceId = ResourceCollectionUtils.getResourceId(taskResource);
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
                Optional<String> resourceId = ResourceCollectionUtils.getResourceId(taskResource);
                if (!resourceId.isPresent()) {
                    logger.error("Failed to find resource ID for resource: {}", taskResource);
                    continue;
                }

                return Optional.of(new ResourceLabels(resourceSpec, resourceId.get()));
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
