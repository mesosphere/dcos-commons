package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;

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
        private final Optional<Protos.Resource> taskResource;

        ReserveEvaluationOutcome(EvaluationOutcome evaluationOutcome, Optional<Protos.Resource> taskResource) {
            this.evaluationOutcome = evaluationOutcome;
            this.taskResource = taskResource;
        }

        /**
         * Returns the evaluation outcome, including a list of offer recommendations for reserve operations to execute,
         * if any.
         */
        EvaluationOutcome getEvaluationOutcome() {
            return evaluationOutcome;
        }

        /**
         * Returns a resource object to be added to the task, or an empty optional if evaluation failed.
         */
        Optional<Protos.Resource> getTaskResource() {
            return taskResource;
        }
    }

    static ReserveEvaluationOutcome evaluateRootVolumeResource(
            OfferEvaluationStage offerEvaluationStage,
            VolumeSpec volumeSpec,
            Optional<String> resourceId,
            Optional<String> persistenceId,
            MesosResourcePool mesosResourcePool) {
        return evaluateResource(
                offerEvaluationStage,
                ResourceBuilder.fromRootVolumeSpec(volumeSpec, resourceId, persistenceId),
                volumeSpec,
                resourceId,
                mesosResourcePool);
    }

    static ReserveEvaluationOutcome evaluateSimpleResource(
            OfferEvaluationStage offerEvaluationStage,
            ResourceSpec resourceSpec,
            Optional<String> resourceId,
            MesosResourcePool mesosResourcePool) {
        return evaluateResource(
                offerEvaluationStage,
                ResourceBuilder.fromSpec(resourceSpec, resourceId),
                resourceSpec,
                resourceId,
                mesosResourcePool);
    }

    private static ReserveEvaluationOutcome evaluateResource(
            OfferEvaluationStage offerEvaluationStage,
            ResourceBuilder resourceBuilder,
            ResourceSpec resourceSpec,
            Optional<String> resourceId,
            MesosResourcePool mesosResourcePool) {
        Optional<MesosResource> mesosResourceOptional;
        if (resourceId.isPresent()) {
            mesosResourceOptional = mesosResourcePool.consumeReserved(
                    resourceSpec.getName(), resourceSpec.getValue(), resourceId.get());
        } else {
            mesosResourceOptional = mesosResourcePool.consumeReservableMerged(
                    resourceSpec.getName(), resourceSpec.getValue(), resourceSpec.getPreReservedRole());
        }
        if (!mesosResourceOptional.isPresent()) {
            return new ReserveEvaluationOutcome(
                    fail(
                            offerEvaluationStage,
                            "Offer failed to satisfy: %s with resourceId: %s",
                            resourceSpec,
                            resourceId)
                            .build(),
                    Optional.empty());
        }

        MesosResource mesosResource = mesosResourceOptional.get();

        if (ValueUtils.equal(mesosResource.getValue(), resourceSpec.getValue())) {
            LOGGER.info("    Resource '{}' matches required value: {}",
                    resourceSpec.getName(),
                    TextFormat.shortDebugString(mesosResource.getValue()),
                    TextFormat.shortDebugString(resourceSpec.getValue()));

            if (!resourceId.isPresent()) {
                // Reuse existing resource as-is
                return new ReserveEvaluationOutcome(
                        pass(
                                offerEvaluationStage,
                                Collections.emptyList(),
                                "Offer contains sufficient previously reserved '%s':" +
                                        " for resource: '%s' with resourceId: '%s'",
                                resourceSpec.getName(),
                                resourceSpec,
                                resourceId)
                                .build(),
                        Optional.of(mesosResource.getResource()));
            } else {
                // Initial reservation of resources
                LOGGER.info("    Resource '{}' requires a RESERVE operation", resourceSpec.getName());
                Protos.Resource resource = resourceBuilder.setMesosResource(mesosResource).build();
                return new ReserveEvaluationOutcome(
                        pass(
                                offerEvaluationStage,
                                Arrays.asList(new ReserveOfferRecommendation(mesosResourcePool.getOffer(), resource)),
                                "Offer contains sufficient '%s': for resource: '%s' with resourceId: '%s'",
                                resourceSpec.getName(),
                                resourceSpec,
                                resourceId)
                                .build(),
                        Optional.of(resource));
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
                            fail(offerEvaluationStage,
                                    "Insufficient resources to increase reservation of resource '%s' with resourceId",
                                    resourceSpec,
                                    resourceId)
                                    .build(),
                            Optional.empty());
                }

                mesosResource = mesosResourceOptional.get();
                Protos.Resource resource = resourceBuilder.setValue(mesosResource.getValue()).build();
                // Reservation of additional resources
                return new ReserveEvaluationOutcome(
                        pass(
                                offerEvaluationStage,
                                Arrays.asList(new ReserveOfferRecommendation(mesosResourcePool.getOffer(), resource)),
                                "Offer contains sufficient '%s': for increasing resource: '%s' with resourceId: '%s'",
                                resourceSpec.getName(),
                                resourceSpec,
                                resourceId)
                                .build(),
                        Optional.of(resource));
            } else {
                LOGGER.info("    Reservation for resource '%s' needs decreasing from current %s to required {}",
                        resourceSpec.getName(),
                        TextFormat.shortDebugString(mesosResource.getValue()),
                        TextFormat.shortDebugString(resourceSpec.getValue()));

                Protos.Value unreserve = ValueUtils.subtract(mesosResource.getValue(), resourceSpec.getValue());
                Protos.Resource resource = resourceBuilder.setValue(unreserve).build();
                // Unreservation of no longer needed resources
                return new ReserveEvaluationOutcome(
                        pass(
                                offerEvaluationStage,
                                Arrays.asList(new UnreserveOfferRecommendation(mesosResourcePool.getOffer(), resource)),
                                "Decreased '%s': for resource: '%s' with resourceId: '%s'",
                                resourceSpec.getName(),
                                resourceSpec,
                                resourceId)
                                .build(),
                        Optional.of(resource));
            }
        }
    }

    public static Optional<String> getRole(PodSpec podSpec) {
        return podSpec.getTasks().stream()
                .map(TaskSpec::getResourceSet)
                .flatMap(resourceSet -> resourceSet.getResources().stream())
                .map(ResourceSpec::getRole)
                .findFirst();
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
}
