package com.mesosphere.sdk.offer.evaluate;

import com.google.inject.Inject;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The OfferEvaluator processes {@link Offer}s and produces {@link OfferRecommendation}s.
 * The determination of what {@link OfferRecommendation}s, if any should be made are made
 * in reference to the {@link OfferRequirement with which it was constructed.  In the
 * case where an OfferRequirement has not been provided no {@link OfferRecommendation}s
 * are ever returned.
 */
public class OfferEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(OfferEvaluator.class);

    private final StateStore stateStore;
    private final OfferRequirementProvider offerRequirementProvider;
    private final String serviceName;
    private final UUID targetConfigId;
    private final SchedulerFlags schedulerFlags;

    @Inject
    public OfferEvaluator(
            StateStore stateStore,
            OfferRequirementProvider offerRequirementProvider,
            String serviceName,
            UUID targetConfigId,
            SchedulerFlags schedulerFlags) {
        this.stateStore = stateStore;
        this.offerRequirementProvider = offerRequirementProvider;
        this.serviceName = serviceName;
        this.targetConfigId = targetConfigId;
        this.schedulerFlags = schedulerFlags;
    }

    public List<OfferRecommendation> evaluate(PodInstanceRequirement podInstanceRequirement, List<Offer> offers)
            throws StateStoreException, InvalidRequirementException {

        for (int i = 0; i < offers.size(); ++i) {
            List<OfferEvaluationStage> evaluationStages = getEvaluationPipeline(podInstanceRequirement);

            Offer offer = offers.get(i);
            MesosResourcePool resourcePool = new MesosResourcePool(offer);
            PodInfoBuilder podInfoBuilder = new PodInfoBuilder(
                    podInstanceRequirement,
                    serviceName,
                    targetConfigId,
                    schedulerFlags);
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

    public List<OfferEvaluationStage> getEvaluationPipeline(PodInstanceRequirement podInstanceRequirement) {
        List<OfferEvaluationStage> evaluationPipeline = new ArrayList<>();
        if (podInstanceRequirement.getPodInstance().getPod().getPlacementRule().isPresent()) {
            evaluationPipeline.add(new PlacementRuleEvaluationStage(
                    stateStore.fetchTasks(),
                    podInstanceRequirement.getPodInstance().getPod().getPlacementRule().get()));
        }

        // TODO: Replace Executor Evaluation
        PodInstance podInstance = podInstanceRequirement.getPodInstance();
        boolean noTasksExist = TaskUtils.getTaskNames(podInstance).stream()
                .map(taskName -> stateStore.fetchTask(taskName))
                .filter(taskInfoOptional -> taskInfoOptional.isPresent())
                .map(taskInfoOptional -> taskInfoOptional.get())
                .count() == 0;

        boolean podHasFailed = podInstanceRequirement.getRecoveryType().equals(RecoveryType.PERMANENT)
                || FailureUtils.isLabeledAsFailed(podInstance, stateStore);

        final String description;
        final boolean shouldGetNewRequirement;
        if (podHasFailed) {
            description = "failed";
            shouldGetNewRequirement = true;
        } else if (noTasksExist) {
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
            evaluationPipeline.addAll(getExistingEvaluationPipeline(podInstanceRequirement));
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
            for (ResourceSpec resourceSpec : entry.getKey().getResources()) {
                evaluationStages.add(new ResourceEvaluationStage(resourceSpec, Optional.empty(), taskName));
            }
            // TODO: Volume evaluation goes here

            evaluationStages.add(new LaunchEvaluationStage(taskName));
        }

        return  evaluationStages;
    }

    private List<OfferEvaluationStage> getExistingEvaluationPipeline(PodInstanceRequirement podInstanceRequirement) {
        List<OfferEvaluationStage> offerEvaluationStages = new ArrayList<>();
        Collection<String> tasksToLaunch = podInstanceRequirement.getTasksToLaunch();

        List<TaskSpec> taskSpecs = new ArrayList<>();
        for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
            if (tasksToLaunch.contains(taskSpec.getName())) {
                taskSpecs.add(taskSpec);
            }
        }
        /*
        List<TaskSpec> taskSpecs = podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
                .filter(name -> podInstanceRequirement.getTasksToLaunch().contains(name))
                .collect(Collectors.toList());
                */

        for (TaskSpec taskSpec : taskSpecs) {
            String taskInfoName = TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec.getName());
            Optional<Protos.TaskInfo> taskInfo = stateStore.fetchTask(taskInfoName);
            if (!taskInfo.isPresent()) {
                logger.error(String.format("Failed to fetch task %s.  Cannot generate resource map.", taskInfoName));
                return Collections.emptyList();
            }

            Map<ResourceSpec, String> resourceSpecStringMap = getResourceSpecIdMap(taskInfo.get(), taskSpec);
            for (Map.Entry<ResourceSpec, String> entry : resourceSpecStringMap.entrySet()) {
                offerEvaluationStages.add(
                        new ResourceEvaluationStage(
                                entry.getKey(), // ResourceSpec
                                Optional.of(entry.getValue()), // ResourceID
                                taskSpec.getName())); // Task name
            }

            offerEvaluationStages.add(new LaunchEvaluationStage(taskSpec.getName()));
        }

        return offerEvaluationStages;
    }

    private Map<ResourceSpec, String> getResourceSpecIdMap(Protos.TaskInfo taskInfo, TaskSpec taskSpec) {
        Map<String, Protos.Resource> resourceMap = taskInfo.getResourcesList().stream()
                .collect(Collectors.toMap(Protos.Resource::getName, Function.identity()));

        Map<ResourceSpec, String> resourceSpecStringMap = new HashMap<>();
        ResourceSet resourceSet = taskSpec.getResourceSet();
        for (ResourceSpec resourceSpec : resourceSet.getResources()) {
            resourceSpecStringMap.put(
                    resourceSpec,
                    ResourceUtils.getResourceId(
                            resourceMap.get(resourceSpec.getName())));
        }

        return resourceSpecStringMap;
    }
}
