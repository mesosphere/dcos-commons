package com.mesosphere.sdk.offer.evaluate;

import com.google.inject.Inject;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import org.apache.mesos.Protos.*;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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

    @Inject
    public OfferEvaluator(StateStore stateStore, OfferRequirementProvider offerRequirementProvider) {
        this.stateStore = stateStore;
        this.offerRequirementProvider = offerRequirementProvider;
    }

    public List<OfferRecommendation> evaluate(PodInstanceRequirement podInstanceRequirement, List<Offer> offers)
            throws StateStoreException, InvalidRequirementException {
        return evaluate(getOfferRequirement(podInstanceRequirement), offers);
    }

    public List<OfferRecommendation> evaluate(OfferRequirement offerRequirement, List<Offer> offers)
            throws StateStoreException, InvalidRequirementException {
        for (int i = 0; i < offers.size(); ++i) {
            List<OfferEvaluationStage> evaluationStages = getEvaluationPipeline(offerRequirement);

            Offer offer = offers.get(i);
            MesosResourcePool resourcePool = new MesosResourcePool(offer);
            PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);
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

    public List<OfferEvaluationStage> getEvaluationPipeline(OfferRequirement offerRequirement) {
        List<OfferEvaluationStage> evaluationPipeline = new ArrayList<>();

        evaluationPipeline.add(new PlacementRuleEvaluationStage(stateStore.fetchTasks()));
        if (offerRequirement.getExecutorRequirementOptional().isPresent()) {
            ExecutorRequirement executorRequirement = offerRequirement.getExecutorRequirementOptional().get();

            // We don't want to re-reserve resources for an already-running executor.
            if (!executorRequirement.isRunningExecutor()) {
                for (ResourceRequirement r : executorRequirement.getResourceRequirements()) {
                    evaluationPipeline.add(r.getEvaluationStage(null));
                }
            }
            evaluationPipeline.add(executorRequirement.getEvaluationStage());
        } else {
            evaluationPipeline.add(new ExecutorEvaluationStage());
        }

        for (TaskRequirement taskRequirement : offerRequirement.getTaskRequirements()) {
            String taskName = taskRequirement.getTaskInfo().getName();
            for (ResourceRequirement r : taskRequirement.getResourceRequirements()) {
                evaluationPipeline.add(r.getEvaluationStage(taskName));
            }

            evaluationPipeline.add(taskRequirement.getEvaluationStage());
        }
        evaluationPipeline.add(new ReservationEvaluationStage(offerRequirement.getResourceIds()));

        return evaluationPipeline;
    }

    private static void logOutcome(StringBuilder stringBuilder, EvaluationOutcome outcome, String indent) {
        stringBuilder.append(String.format("  %s%s%n", indent, outcome.toString()));
        for (EvaluationOutcome child : outcome.getChildren()) {
            logOutcome(stringBuilder, child, indent + "  ");
        }
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
        logger.info("Generating requirement for {} pod '{}' containing tasks: {}",
                description, podInstance.getName(), podInstanceRequirement.getTasksToLaunch());

        OfferRequirement offerRequirement = null;
        if (shouldGetNewRequirement) {
            offerRequirement = offerRequirementProvider.getNewOfferRequirement(podInstanceRequirement);
        } else {
            offerRequirement = offerRequirementProvider.getExistingOfferRequirement(podInstanceRequirement);
        }

        logger.debug("OfferRequirement: {}", offerRequirement);
        return offerRequirement;
    }
}
