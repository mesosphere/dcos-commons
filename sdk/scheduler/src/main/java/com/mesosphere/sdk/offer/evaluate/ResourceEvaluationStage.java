package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.mesos.Protos.Resource;

import java.util.Optional;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement},
 * ensuring that it contains a sufficient amount or value of the supplied {@link Resource}, and creating a
 * {@link ReserveOfferRecommendation} or {@link UnreserveOfferRecommendation} where necessary.
 */
public class ResourceEvaluationStage implements OfferEvaluationStage {
    private final String taskName;
    private final Optional<String> requiredResourceId;
    private final ResourceSpec resourceSpec;

    /**
     * Creates a new instance for basic resource evaluation.
     *
     * @param resourceSpec the resource spec to be evaluated
     * @param requiredResourceId any previously reserved resource ID to be required, or empty for a new reservation
     * @param taskName the name of the task which will use this resource
     */
    public ResourceEvaluationStage(ResourceSpec resourceSpec, Optional<String> requiredResourceId, String taskName) {
        this.resourceSpec = resourceSpec;
        this.requiredResourceId = requiredResourceId;
        this.taskName = taskName;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        OfferEvaluationUtils.ReserveEvaluationOutcome reserveEvaluationOutcome =
                OfferEvaluationUtils.evaluateSimpleResource(this, resourceSpec, requiredResourceId, mesosResourcePool);

        EvaluationOutcome evaluationOutcome = reserveEvaluationOutcome.getEvaluationOutcome();
        if (!evaluationOutcome.isPassing()) {
            return evaluationOutcome;
        }

        // Use the reservation outcome's resourceId, which is a newly generated UUID if requiredResourceId was empty.
        OfferEvaluationUtils.setProtos(
                podInfoBuilder,
                ResourceBuilder.fromSpec(resourceSpec, reserveEvaluationOutcome.getResourceId()).build(),
                Optional.ofNullable(taskName));

        return evaluationOutcome;
    }
}
