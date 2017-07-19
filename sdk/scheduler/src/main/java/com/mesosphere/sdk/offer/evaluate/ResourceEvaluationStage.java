package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.ReserveOfferRecommendation;
import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.offer.UnreserveOfferRecommendation;
import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.mesos.Protos.Resource;

import java.util.Collections;
import java.util.Optional;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement},
 * ensuring that it contains a sufficient amount or value of the supplied {@link Resource}, and creating a
 * {@link ReserveOfferRecommendation} or {@link UnreserveOfferRecommendation} where necessary.
 */
public class ResourceEvaluationStage implements OfferEvaluationStage {
    private final String taskName;
    private final Optional<String> requiredResourceId;
    private final ResourceSpec resourceSpec;
    private final Optional<String> persistenceId;

    /**
     * Creates a new instance for basic resource evaluation.
     *
     * @param resourceSpec the resource spec to be evaluated
     * @param requiredResourceId any previously reserved resource ID to be required, or empty for a new reservation
     * @param taskName the name of the task which will use this resource
     */
    public ResourceEvaluationStage(ResourceSpec resourceSpec, Optional<String> requiredResourceId,
            Optional<String> persistenceId, String taskName) {
        this.resourceSpec = resourceSpec;
        this.requiredResourceId = requiredResourceId;
        this.taskName = taskName;
        this.persistenceId = persistenceId;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        boolean isRunningExecutor =
                OfferEvaluationUtils.isRunningExecutor(podInfoBuilder, mesosResourcePool.getOffer());
        if (taskName == null && isRunningExecutor && requiredResourceId.isPresent()) {
            // This is a resource on a running executor, so it isn't present in the offer, but we need to make sure to
            // add it to the ExecutorInfo.

            OfferEvaluationUtils.setProtos(
                    podInfoBuilder,
                    ResourceBuilder.fromSpec(resourceSpec, requiredResourceId).build(),
                    Optional.ofNullable(taskName));
            return pass(
                    this,
                    Collections.emptyList(),
                    "Setting info for already running Executor with existing resource with resourceId: '%s'",
                    requiredResourceId)
                    .build();
        }

        OfferEvaluationUtils.ReserveEvaluationOutcome reserveEvaluationOutcome =
                OfferEvaluationUtils.evaluateSimpleResource(this, resourceSpec, requiredResourceId, persistenceId,
                        mesosResourcePool);

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
