package com.mesosphere.sdk.offer.evaluate;

import com.google.inject.Inject;
import com.google.protobuf.TextFormat;
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
        List<OfferRecommendation> recommendations = Collections.emptyList();
        for (int i = 0; i < offers.size(); ++i) {
            if (!recommendations.isEmpty()) {
                break;
            }

            List<OfferEvaluationStage> evaluationStages = getEvaluationPipeline(offerRequirement);

            Offer offer = offers.get(i);
            MesosResourcePool resourcePool = new MesosResourcePool(offer);
            PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);
            List<EvaluationOutcome> outcomes = new ArrayList<>();
            int failedOutcomeCount = 0;

            for (OfferEvaluationStage evaluationStage : evaluationStages) {
                EvaluationOutcome outcome =
                        evaluationStage.evaluate(resourcePool, podInfoBuilder);
                outcomes.add(outcome);
                if (!outcome.isPassing()) {
                    failedOutcomeCount++;
                }
            }

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("\n");
            for (EvaluationOutcome outcome : outcomes) {
                logOutcome(stringBuilder, outcome, "");
            }
            logger.info(stringBuilder.toString().trim());

            if (failedOutcomeCount != 0) {
                recommendations.clear();
                logger.info("- {}: failed {} of {} evaluation stages.",
                        i + 1, failedOutcomeCount, evaluationStages.size());

                continue;
            }

            recommendations = getRecommendations(outcomes);
            logger.info("- {}: passed all {} evaluation stages, returning {} recommendations: {}",
                    i + 1, evaluationStages.size(), recommendations.size(), TextFormat.shortDebugString(offer));
        }

        return recommendations;
    }

    public List<OfferEvaluationStage> getEvaluationPipeline(OfferRequirement offerRequirement) {
        List<OfferEvaluationStage> evaluationPipeline = new ArrayList<>();

        evaluationPipeline.add(new PlacementRuleEvaluationStage(stateStore.fetchTasks()));
        if (offerRequirement.getExecutorRequirementOptional().isPresent()) {
            evaluationPipeline.add(offerRequirement.getExecutorRequirementOptional().get().getEvaluationStage());
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

    private static List<OfferRecommendation> getRecommendations(Collection<EvaluationOutcome> outcomes) {
        return outcomes.stream()
                .map(outcome -> outcome.getOfferRecommendations())
                .flatMap(xs -> xs.stream())
                .collect(Collectors.toList());
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
        if (shouldGetNewRequirement) {
            return offerRequirementProvider.getNewOfferRequirement(podInstanceRequirement);
        } else {
            return offerRequirementProvider.getExistingOfferRequirement(podInstanceRequirement);
        }
    }
}
