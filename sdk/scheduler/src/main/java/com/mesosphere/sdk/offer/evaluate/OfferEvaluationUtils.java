package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.fail;
import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class encapsulates shared offer evaluation logic for evaluation stages.
 */
class OfferEvaluationUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(OfferEvaluationUtils.class);

    private OfferEvaluationUtils() {
        // Do not instantiate this class.
    }

    static class ReserveEvaluationOutcome {
        private final EvaluationOutcome evaluationOutcome;
        private final String resourceId;

        ReserveEvaluationOutcome(EvaluationOutcome evaluationOutcome, String resourceId) {
            this.evaluationOutcome = evaluationOutcome;
            this.resourceId = resourceId;
        }

        EvaluationOutcome getEvaluationOutcome() {
            return evaluationOutcome;
        }

        Optional<String> getResourceId() {
            return Optional.ofNullable(resourceId);
        }
    }

    static ReserveEvaluationOutcome evaluateSimpleResource (
            OfferEvaluationStage offerEvaluationStage,
            ResourceSpec resourceSpec,
            Optional<String> resourceId,
            MesosResourcePool mesosResourcePool) {

        Optional<MesosResource> mesosResourceOptional = consume(resourceSpec, resourceId, mesosResourcePool);
        if (!mesosResourceOptional.isPresent()) {
            return new ReserveEvaluationOutcome(
                    fail(
                            offerEvaluationStage,
                            "Offer failed to satisfy: %s with resourceId: %s",
                            resourceSpec,
                            resourceId),
                    null);
        }

        OfferRecommendation offerRecommendation = null;
        MesosResource mesosResource = mesosResourceOptional.get();

        if (ValueUtils.equal(mesosResource.getValue(), resourceSpec.getValue())) {
            LOGGER.info("    Resource '{}' matches required value: {}",
                    resourceSpec.getName(),
                    TextFormat.shortDebugString(mesosResource.getValue()),
                    TextFormat.shortDebugString(resourceSpec.getValue()));

            if (!resourceId.isPresent()) {
                // Initial reservation of resources
                LOGGER.info("    Resource '{}' requires a RESERVE operation", resourceSpec.getName());
                Protos.Resource resource = ResourceBuilder.fromSpec(resourceSpec, resourceId).build();
                offerRecommendation = new ReserveOfferRecommendation(mesosResourcePool.getOffer(), resource);
                return new ReserveEvaluationOutcome(
                        pass(
                                offerEvaluationStage,
                                Arrays.asList(offerRecommendation),
                                "Offer contains sufficient '%s': for resource: '%s' with resourceId: '%s'",
                                resourceSpec.getName(),
                                resourceSpec,
                                resourceId),
                        ResourceUtils.getResourceId(resource).get());
            } else {
                return new ReserveEvaluationOutcome(
                        pass(
                                offerEvaluationStage,
                                Collections.emptyList(),
                                "Offer contains sufficient previously reserved '%s':" +
                                        " for resource: '%s' with resourceId: '%s'",
                                resourceSpec.getName(),
                                resourceSpec,
                                resourceId),
                        resourceId.get());
            }
        } else {
            Protos.Value difference = ValueUtils.subtract(resourceSpec.getValue(), mesosResource.getValue());
            if (ValueUtils.compare(difference, ValueUtils.getZero(difference.getType())) > 0) {
                LOGGER.info("    Reservation for resource '{}' needs increasing from current {} to required {}",
                        resourceSpec.getName(),
                        TextFormat.shortDebugString(mesosResource.getValue()),
                        TextFormat.shortDebugString(resourceSpec.getValue()));

                ResourceSpec requiredAdditionalResources = DefaultResourceSpec.newBuilder(resourceSpec)
                        .value(difference)
                        .build();
                mesosResourceOptional = mesosResourcePool.consumeReservableMerged(
                        requiredAdditionalResources.getName(),
                        requiredAdditionalResources.getValue(),
                        Constants.ANY_ROLE);

                if (!mesosResourceOptional.isPresent()) {
                    return new ReserveEvaluationOutcome(
                            fail(
                                    offerEvaluationStage,
                                    "Insufficient resources to increase reservation of resource '%s' with resourceId",
                                    resourceSpec,
                                    resourceId),
                            null);
                }

                mesosResource = mesosResourceOptional.get();
                Protos.Resource resource = ResourceBuilder.fromSpec(resourceSpec, resourceId)
                        .setValue(mesosResource.getValue())
                        .build();
                // Reservation of additional resources
                offerRecommendation = new ReserveOfferRecommendation(
                        mesosResourcePool.getOffer(),
                        resource);
                return new ReserveEvaluationOutcome(
                        pass(
                                offerEvaluationStage,
                                Arrays.asList(offerRecommendation),
                                "Offer contains sufficient '%s': for increasing resource: '%s' with resourceId: '%s'",
                                resourceSpec.getName(),
                                resourceSpec,
                                resourceId),
                        ResourceUtils.getResourceId(resource).get());
            } else {
                LOGGER.info("    Reservation for resource '%s' needs decreasing from current %s to required {}",
                        resourceSpec.getName(),
                        TextFormat.shortDebugString(mesosResource.getValue()),
                        TextFormat.shortDebugString(resourceSpec.getValue()));

                Protos.Value unreserve = ValueUtils.subtract(mesosResource.getValue(), resourceSpec.getValue());
                Protos.Resource resource = ResourceBuilder.fromSpec(resourceSpec, resourceId)
                        .setValue(unreserve)
                        .build();
                // Unreservation of no longer needed resources
                offerRecommendation = new UnreserveOfferRecommendation(
                        mesosResourcePool.getOffer(),
                        resource);
                return new ReserveEvaluationOutcome(
                        pass(
                                offerEvaluationStage,
                                Arrays.asList(offerRecommendation),
                                "Decreased '%s': for resource: '%s' with resourceId: '%s'",
                                resourceSpec.getName(),
                                resourceSpec,
                                resourceId),
                        ResourceUtils.getResourceId(resource).get());
            }
        }
    }

    static void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource, Optional<String> taskName) {
        if (taskName.isPresent()) {
            Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName.get());
            taskBuilder.addResources(resource);
        } else {
            Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
            executorBuilder.addResources(resource);
        }
    }


    private static Optional<MesosResource> consume(
            ResourceSpec resourceSpec,
            Optional<String> resourceId,
            MesosResourcePool pool) {

        if (!resourceId.isPresent()) {
            return pool.consumeReservableMerged(
                    resourceSpec.getName(),
                    resourceSpec.getValue(),
                    Constants.ANY_ROLE);
        } else {
            return pool.consumeReserved(resourceSpec.getName(), resourceSpec.getValue(), resourceId.get());
        }
    }
}
