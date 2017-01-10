package com.mesosphere.sdk.offer.evaluate;

import com.google.inject.Inject;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import org.apache.mesos.Protos.*;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Inject
    public OfferEvaluator(StateStore stateStore, OfferRequirementProvider offerRequirementProvider) {
        this.stateStore = stateStore;
        this.offerRequirementProvider = offerRequirementProvider;
    }

    public List<OfferRecommendation> evaluate(PodInstanceRequirement podInstanceRequirement, List<Offer> offers)
            throws StateStoreException, InvalidRequirementException {
        OfferRequirement offerRequirement = getOfferRequirement(podInstanceRequirement);
        List<OfferEvaluationStage> evaluationStages = getEvaluationPipeline(podInstanceRequirement, offerRequirement);
        List<OfferRecommendation> recommendations = Collections.emptyList();

        for (int i = 0; i < offers.size(); ++i) {
            if (!recommendations.isEmpty()) {
                break;
            }

            Offer offer = offers.get(i);
            MesosResourcePool resourcePool = new MesosResourcePool(offer);
            OfferRecommendationSlate recommendationSlate = new OfferRecommendationSlate();
            List<String> failureNotifications = new ArrayList<>();
            for (OfferEvaluationStage evaluationStage : evaluationStages) {
                try {
                    evaluationStage.evaluate(
                            resourcePool,
                            offerRequirement,
                            recommendationSlate);
                } catch (OfferEvaluationException e) {
                    failureNotifications.add(evaluationStage.getClass().getName() + e.getMessage());
                }
            }

            if (!failureNotifications.isEmpty()) {
                logger.info("- {}: failed {} evaluation stages out of {} for the following reasons:",
                        i + 1, failureNotifications.size(), evaluationStages.size());
                for (String notification : failureNotifications) {
                    logger.info("-    {}", notification);
                }

                continue;
            }

            recommendations = recommendationSlate.getRecommendations();
            logger.info("- {}: passed resource requirements, returning {} recommendations: {}",
                    i + 1, recommendations.size(), TextFormat.shortDebugString(offer));
        }

        return recommendations;
    }

    private List<OfferEvaluationStage> getEvaluationPipeline(
            PodInstanceRequirement podInstanceRequirement,
            OfferRequirement offerRequirement) throws InvalidRequirementException {
        List<OfferEvaluationStage> evaluationPipeline = new ArrayList<>();

        evaluationPipeline.add(new PlacementRuleEvaluationStage(stateStore.fetchTasks()));
        if (offerRequirement.getExecutorRequirementOptional().isPresent() &&
                !offerRequirement.getExecutorRequirementOptional().get()
                        .getExecutorInfo()
                        .getExecutorId()
                        .getValue()
                        .isEmpty()) {
            evaluationPipeline.add(new ExecutorEvaluationStage(
                    offerRequirement.getExecutorRequirementOptional().get()
                            .getExecutorInfo()
                            .getExecutorId()));
        } else {
            evaluationPipeline.add(new ExecutorEvaluationStage());
        }

        Set<String> tasksToLaunch = Stream.concat(
                podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
                        .filter(t -> podInstanceRequirement.getTasksToLaunch().contains(t.getName()))
                        .map(t -> TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), t)),
                offerRequirement.getTaskRequirements().stream()
                        .map(t -> t.getTaskInfo().getName()))
                .collect(Collectors.toSet());
        for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
            String taskName = TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec);
            if (tasksToLaunch.contains(taskName)) {
                for (ResourceSpec r : taskSpec.getResourceSet().getResources()) {
                    Resource taskResource = ResourceUtils.getResource(
                            offerRequirement.getTaskRequirement(taskName).getTaskInfo(), r.getName());
                    evaluationPipeline.add(r.getEvaluationStage(taskResource, taskName));
                }

                for (VolumeSpec v : taskSpec.getResourceSet().getVolumes()) {
                    Resource taskResource = ResourceUtils.getResource(
                            offerRequirement.getTaskRequirement(taskName).getTaskInfo(), v.getName());
                    evaluationPipeline.add(v.getEvaluationStage(taskResource, taskName));
                }
                evaluationPipeline.add(new LaunchEvaluationStage(taskName));
            }
        }
        evaluationPipeline.add(new ReservationEvaluationStage(offerRequirement.getResourceIds()));

        return evaluationPipeline;
    }

    private OfferRequirement getOfferRequirement(PodInstanceRequirement podInstanceRequirement)
            throws InvalidRequirementException {
        PodInstance podInstance = podInstanceRequirement.getPodInstance();
        boolean noTasksExist = TaskUtils.getTaskNames(podInstance).stream()
                .map(taskName -> stateStore.fetchTask(taskName))
                .filter(taskInfoOptional -> taskInfoOptional.isPresent())
                .map(taskInfoOptional -> taskInfoOptional.get())
                .count() == 0;

        final String description;
        final boolean shouldGetNewRequirement;
        if (podInstanceRequirement.isPermanentReplacement()) {
            description = "failed";
            shouldGetNewRequirement = true;
        } else if (noTasksExist) {
            description = "new";
            shouldGetNewRequirement = true;
        } else {
            description = "existing";
            shouldGetNewRequirement = false;
        }
        Collection<String> tasksToLaunch = podInstanceRequirement.getTasksToLaunch();
        logger.info("Generating requirement for {} pod '{}' containing tasks: {}",
                description, podInstance.getName(), tasksToLaunch);
        if (shouldGetNewRequirement) {
            return offerRequirementProvider.getNewOfferRequirement(podInstance, tasksToLaunch);
        } else {
            return offerRequirementProvider.getExistingOfferRequirement(podInstance, tasksToLaunch);
        }
    }
}
