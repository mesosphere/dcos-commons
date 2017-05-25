package com.mesosphere.sdk.offer.evaluate;

import com.google.inject.Inject;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The OfferEvaluator processes {@link Protos.Offer}s and produces {@link OfferRecommendation}s.
 * The determination of what {@link OfferRecommendation}s, if any should be made are made
 * in reference to the {@link OfferRequirement with which it was constructed.  In the
 * case where an OfferRequirement has not been provided no {@link OfferRecommendation}s
 * are ever returned.
 */
public class OfferEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(OfferEvaluator.class);

    private final StateStore stateStore;
    private final String serviceName;
    private final UUID targetConfigId;
    private final SchedulerFlags schedulerFlags;

    @Inject
    public OfferEvaluator(
            StateStore stateStore,
            String serviceName,
            UUID targetConfigId,
            SchedulerFlags schedulerFlags) {
        this.stateStore = stateStore;
        this.serviceName = serviceName;
        this.targetConfigId = targetConfigId;
        this.schedulerFlags = schedulerFlags;
    }

    public List<OfferRecommendation> evaluate(PodInstanceRequirement podInstanceRequirement, List<Protos.Offer> offers)
            throws StateStoreException, InvalidRequirementException {

        // All tasks in the service (used by some PlacementRules):
        Map<String, Protos.TaskInfo> allTasks = stateStore.fetchTasks().stream()
                .collect(Collectors.toMap(Protos.TaskInfo::getName, Function.identity()));
        // Preexisting tasks for this pod (if any):
        Map<String, Protos.TaskInfo> thisPodTasks =
                TaskUtils.getTaskNames(podInstanceRequirement.getPodInstance()).stream()
                .map(taskName -> allTasks.get(taskName))
                .filter(taskInfo -> taskInfo != null)
                .collect(Collectors.toMap(Protos.TaskInfo::getName, Function.identity()));

        for (int i = 0; i < offers.size(); ++i) {
            List<OfferEvaluationStage> evaluationStages =
                    getEvaluationPipeline(podInstanceRequirement, allTasks.values(), thisPodTasks);

            Protos.Offer offer = offers.get(i);
            MesosResourcePool resourcePool = new MesosResourcePool(offer);
            PodInfoBuilder podInfoBuilder = new PodInfoBuilder(
                    podInstanceRequirement, serviceName, targetConfigId, schedulerFlags, thisPodTasks.values());
            List<EvaluationOutcome> outcomes = new ArrayList<>();
            int failedOutcomeCount = 0;

            for (OfferEvaluationStage evaluationStage : evaluationStages) {
                EvaluationOutcome outcome = evaluationStage.evaluate(resourcePool, podInfoBuilder);
                outcomes.add(outcome);
                if (!outcome.isPassing()) {
                    failedOutcomeCount++;
                }
            }

            StringBuilder outcomeDetails = new StringBuilder();
            for (EvaluationOutcome outcome : outcomes) {
                logOutcome(outcomeDetails, outcome, "");
            }
            if (outcomeDetails.length() != 0) {
                // trim extra trailing newline:
                outcomeDetails.deleteCharAt(outcomeDetails.length() - 1);
            }

            if (failedOutcomeCount != 0) {
                logger.info("Offer {}: failed {} of {} evaluation stages:\n{}",
                        i + 1, failedOutcomeCount, evaluationStages.size(), outcomeDetails.toString());
            } else {
                List<OfferRecommendation> recommendations = outcomes.stream()
                        .map(outcome -> outcome.getOfferRecommendations())
                        .flatMap(xs -> xs.stream())
                        .collect(Collectors.toList());
                logger.info("Offer {}: passed all {} evaluation stages, returning {} recommendations:\n{}",
                        i + 1, evaluationStages.size(), recommendations.size(), outcomeDetails.toString());
                return recommendations;
            }
        }

        return Collections.emptyList();
    }

    public List<OfferEvaluationStage> getEvaluationPipeline(
            PodInstanceRequirement podInstanceRequirement,
            Collection<Protos.TaskInfo> allTasks,
            Map<String, Protos.TaskInfo> thisPodTasks) {
        List<OfferEvaluationStage> evaluationPipeline = new ArrayList<>();
        if (podInstanceRequirement.getPodInstance().getPod().getPlacementRule().isPresent()) {
            evaluationPipeline.add(new PlacementRuleEvaluationStage(
                    allTasks, podInstanceRequirement.getPodInstance().getPod().getPlacementRule().get()));
        }

        // TODO: Replace Executor Evaluation
        PodInstance podInstance = podInstanceRequirement.getPodInstance();
        boolean noLaunchedTasksExist = thisPodTasks.values().stream()
                .flatMap(taskInfo -> taskInfo.getResourcesList().stream())
                .map(resource -> ResourceUtils.getResourceId(resource))
                .filter(resourceId -> resourceId != null && !resourceId.isEmpty())
                .count() == 0;

        boolean podHasFailed = podInstanceRequirement.getRecoveryType().equals(RecoveryType.PERMANENT)
                || FailureUtils.isLabeledAsFailed(podInstance, stateStore);

        final String description;
        final boolean shouldGetNewRequirement;
        if (podHasFailed) {
            description = "failed";
            shouldGetNewRequirement = true;
        } else if (noLaunchedTasksExist) {
            description = "new";
            shouldGetNewRequirement = true;
        } else {
            description = "existing";
            shouldGetNewRequirement = false;
        }
        logger.info("Generating requirement for {} pod '{}' containing tasks: {}",
                description, podInstance.getName(), podInstanceRequirement.getTasksToLaunch());

        if (shouldGetNewRequirement) {
            evaluationPipeline.addAll(getNewEvaluationPipeline(podInstanceRequirement));
        } else {
            evaluationPipeline.addAll(getExistingEvaluationPipeline(podInstanceRequirement, thisPodTasks));
        }

        return evaluationPipeline;
    }

    private static void logOutcome(StringBuilder stringBuilder, EvaluationOutcome outcome, String indent) {
        stringBuilder.append(String.format("  %s%s%n", indent, outcome.toString()));
        for (EvaluationOutcome child : outcome.getChildren()) {
            logOutcome(stringBuilder, child, indent + "  ");
        }
    }

    private List<OfferEvaluationStage> getNewEvaluationPipeline(PodInstanceRequirement podInstanceRequirement) {
        Map<ResourceSet, String> resourceSets = podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
                .collect(Collectors.toMap(TaskSpec::getResourceSet, TaskSpec::getName));

        List<OfferEvaluationStage> evaluationStages = new ArrayList<>();
        for (Map.Entry<ResourceSet, String> entry : resourceSets.entrySet()) {
            String taskName = entry.getValue();
            List<ResourceSpec> resourceSpecs = getOrderedResourceSpecs(entry.getKey());
            for (ResourceSpec resourceSpec : resourceSpecs) {
                if (resourceSpec instanceof NamedVIPSpec) {
                    evaluationStages.add(new NamedVIPEvaluationStage(
                            (NamedVIPSpec) resourceSpec, taskName, Optional.empty()));
                } else if (resourceSpec instanceof PortSpec) {
                    evaluationStages.add(new PortEvaluationStage((PortSpec) resourceSpec, taskName, Optional.empty()));
                } else {
                    evaluationStages.add(new ResourceEvaluationStage(resourceSpec, Optional.empty(), taskName));
                }
            }

            for (VolumeSpec volumeSpec : entry.getKey().getVolumes()) {
                evaluationStages.add(
                        new VolumeEvaluationStage(
                                volumeSpec,
                                taskName,
                                Optional.empty(),
                                Optional.empty()));
            }

            evaluationStages.add(new LaunchEvaluationStage(taskName));
        }

        return  evaluationStages;
    }

    private List<ResourceSpec> getOrderedResourceSpecs(ResourceSet resourceSet) {
        // Statically defined ports, then dynamic ports, then everything else
        List<ResourceSpec> staticPorts = new ArrayList<>();
        List<ResourceSpec> dynamicPorts = new ArrayList<>();
        List<ResourceSpec> simpleResources = new ArrayList<>();

        for (ResourceSpec resourceSpec : resourceSet.getResources()) {
            if (resourceSpec instanceof PortSpec) {
                if (((PortSpec) resourceSpec).getPort() == 0) {
                    dynamicPorts.add(resourceSpec);
                } else {
                    staticPorts.add(resourceSpec);
                }
            } else {
                simpleResources.add(resourceSpec);
            }
        }

        List<ResourceSpec> resourceSpecs = new ArrayList<>();
        resourceSpecs.addAll(staticPorts);
        resourceSpecs.addAll(dynamicPorts);
        resourceSpecs.addAll(simpleResources);
        return resourceSpecs;
    }

    private static List<OfferEvaluationStage> getExistingEvaluationPipeline(
            PodInstanceRequirement podInstanceRequirement, Map<String, Protos.TaskInfo> podTasks) {
        List<OfferEvaluationStage> offerEvaluationStages = new ArrayList<>();
        List<TaskSpec> taskSpecs = podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
                .filter(taskSpec -> podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName()))
                .collect(Collectors.toList());

        for (TaskSpec taskSpec : taskSpecs) {
            String taskInfoName = TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec.getName());
            Protos.TaskInfo taskInfo = podTasks.get(taskInfoName);
            if (taskInfo == null) {
                logger.error(String.format("Failed to fetch task %s.  Cannot generate resource map.", taskInfoName));
                return Collections.emptyList();
            }

            // ResourceSpec => ResourceID
            Map<ResourceSpec, String> resourceSpecIdMap = getResourceSpecIdMap(taskInfo, taskSpec);
            for (Map.Entry<ResourceSpec, String> entry : resourceSpecIdMap.entrySet()) {
                if (entry.getKey() instanceof NamedVIPSpec) {
                    offerEvaluationStages.add(new NamedVIPEvaluationStage(
                            (NamedVIPSpec) entry.getKey(), taskSpec.getName(), Optional.of(entry.getValue())));
                } else if (entry.getKey() instanceof PortSpec) {
                    offerEvaluationStages.add(new PortEvaluationStage(
                            (PortSpec) entry.getKey(), taskSpec.getName(), Optional.of(entry.getValue())));
                } else {
                    offerEvaluationStages.add(new ResourceEvaluationStage(
                            entry.getKey(), Optional.of(entry.getValue()), taskSpec.getName()));
                }
            }

            // VolumeSpec => ResourceID + PersistenceID
            Map<VolumeSpec, Pair<String, String>> volumeSpecIdMap = getVolumeSpecIdMap(taskInfo, taskSpec);
            for (Map.Entry<VolumeSpec, Pair<String, String>> entry : volumeSpecIdMap.entrySet()) {
                offerEvaluationStages.add(new VolumeEvaluationStage(
                        entry.getKey(),
                        taskSpec.getName(),
                        Optional.of(entry.getValue().getLeft()),
                        Optional.of(entry.getValue().getRight())));
            }


            offerEvaluationStages.add(new LaunchEvaluationStage(taskSpec.getName()));
        }

        return offerEvaluationStages;
    }

    private static Map<ResourceSpec, String> getResourceSpecIdMap(Protos.TaskInfo taskInfo, TaskSpec taskSpec) {
        Map<String, Protos.Resource> resourceMap = taskInfo.getResourcesList().stream()
                .collect(Collectors.toMap(Protos.Resource::getName, Function.identity()));

        Map<ResourceSpec, String> resourceSpecStringMap = new HashMap<>();
        ResourceSet resourceSet = taskSpec.getResourceSet();
        for (ResourceSpec resourceSpec : resourceSet.getResources()) {
            resourceSpecStringMap.put(
                    resourceSpec, ResourceUtils.getResourceId(resourceMap.get(resourceSpec.getName())));
        }

        return resourceSpecStringMap;
    }

    private static Map<VolumeSpec, Pair<String, String>> getVolumeSpecIdMap(
            Protos.TaskInfo taskInfo, TaskSpec taskSpec) {
        List<Protos.Resource> resourceList = taskInfo.getResourcesList().stream()
                .filter(resource -> resource.hasDisk())
                .filter(resource -> resource.getDisk().hasVolume())
                .collect(Collectors.toList());

        // ContainerPath --> PersistenceId
        Map<String, String> persistenceMap = new HashMap<>();
        for (Protos.Resource resource : resourceList) {
            String containerPath = resource.getDisk().getVolume().getContainerPath();
            persistenceMap.put(containerPath, resource.getDisk().getPersistence().getId());
        }

        // ContainerPath --> ResourceId
        Map<String, String> resourceMap = new HashMap<>();
        for (Protos.Resource resource : resourceList) {
            String containerPath = resource.getDisk().getVolume().getContainerPath();
            resourceMap.put(containerPath, ResourceUtils.getResourceId(resource));
        }

        Map<VolumeSpec, Pair<String, String>> volumeSpecIdMap = new HashMap<>();
        ResourceSet resourceSet = taskSpec.getResourceSet();
        for (VolumeSpec volumeSpec: resourceSet.getVolumes()) {
            volumeSpecIdMap.put(
                    volumeSpec,
                    Pair.of(
                            resourceMap.get(volumeSpec.getContainerPath()),
                            persistenceMap.get(volumeSpec.getContainerPath())));
        }

        return volumeSpecIdMap;
    }
}
