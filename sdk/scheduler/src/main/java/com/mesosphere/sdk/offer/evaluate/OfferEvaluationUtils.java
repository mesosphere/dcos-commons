package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
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

    /**
     * An extension of an evaluation outcome which also includes a {@link Protos.Resource} to be stored against the
     * launched {@link Protos.TaskInfo}.
     */
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
         * Returns a resource object to be added to the {@link Protos.TaskInfo} in the launch operation, or an empty
         * optional if evaluation failed.
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
        if (!resourceId.isPresent()) {
            mesosResourceOptional = mesosResourcePool.consumeReservableMerged(
                    resourceSpec.getName(), resourceSpec.getValue(), resourceSpec.getPreReservedRole());
            if (!mesosResourceOptional.isPresent()) {
                Optional<Protos.Value> availableValue = mesosResourcePool.getAvailableReservableMerged(
                        resourceSpec.getName(), resourceSpec.getPreReservedRole());
                return new ReserveEvaluationOutcome(
                        fail(
                                offerEvaluationStage,
                                "Offer lacks required '%s' (need [%s], offered [%s]): '%s'",
                                resourceSpec.getName(),
                                TextFormat.shortDebugString(resourceSpec.getValue()),
                                availableValue.isPresent() ? TextFormat.shortDebugString(availableValue.get()) : "NULL",
                                resourceSpec)
                                .build(),
                        Optional.empty());
            }
        } else {
            mesosResourceOptional = mesosResourcePool.consumeReserved(
                    resourceSpec.getName(), resourceSpec.getValue(), resourceId.get());
            if (!mesosResourceOptional.isPresent()) {
                return new ReserveEvaluationOutcome(
                        fail(
                                offerEvaluationStage,
                                "Offer lacks required '%s' with resource id '%s' (need [%s]): '%s'",
                                resourceSpec.getName(),
                                resourceId.get(),
                                TextFormat.shortDebugString(resourceSpec.getValue()),
                                resourceSpec)
                                .build(),
                        Optional.empty());
            }
        }

        MesosResource mesosResource = mesosResourceOptional.get();

        if (ValueUtils.equal(mesosResource.getValue(), resourceSpec.getValue())) {
            LOGGER.info("    Resource '{}' matches required value: {}",
                    resourceSpec.getName(),
                    TextFormat.shortDebugString(mesosResource.getValue()),
                    TextFormat.shortDebugString(resourceSpec.getValue()));

            if (!resourceId.isPresent()) {
                // Initial reservation of resources
                LOGGER.info("    Resource '{}' requires a RESERVE operation", resourceSpec.getName());
                Protos.Resource resource = resourceBuilder.setMesosResource(mesosResource).build();
                return new ReserveEvaluationOutcome(
                        pass(
                                offerEvaluationStage,
                                Arrays.asList(new ReserveOfferRecommendation(mesosResourcePool.getOffer(), resource)),
                                "Offer contains sufficient '%s' (need [%s]): '%s'",
                                resourceSpec.getName(),
                                TextFormat.shortDebugString(resourceSpec.getValue()),
                                resourceSpec)
                                .build(),
                        Optional.of(resource));
            } else {
                // Reuse existing resource as-is
                return new ReserveEvaluationOutcome(
                        pass(
                                offerEvaluationStage,
                                Collections.emptyList(),
                                "Offer contains previously reserved '%s' with resource id '%s' (need [%s]): '%s'",
                                resourceSpec.getName(),
                                resourceId.get(),
                                TextFormat.shortDebugString(resourceSpec.getValue()),
                                resourceSpec)
                                .build(),
                        Optional.of(mesosResource.getResource()));
            }
        } else {
            Protos.Value currentReservedValue = mesosResource.getValue();
            Protos.Value amountToReserve = ValueUtils.subtract(resourceSpec.getValue(), currentReservedValue);
            if (ValueUtils.compare(amountToReserve, ValueUtils.getZero(amountToReserve.getType())) > 0) {
                LOGGER.info(
                        "    Reservation for resource '{}' needs increasing by [{}] from current [{}] to required [{}]",
                        resourceSpec.getName(),
                        TextFormat.shortDebugString(amountToReserve),
                        TextFormat.shortDebugString(currentReservedValue),
                        TextFormat.shortDebugString(resourceSpec.getValue()));

                Optional<MesosResource> mesosResourceToReserveOptional = mesosResourcePool.consumeReservableMerged(
                        resourceSpec.getName(), amountToReserve, Constants.ANY_ROLE);

                if (!mesosResourceToReserveOptional.isPresent()) {
                    // Not enough additional space to grow this reservation.
                    Optional<Protos.Value> availableValue =
                            mesosResourcePool.getAvailableReservableMerged(resourceSpec.getName(), Constants.ANY_ROLE);
                    return new ReserveEvaluationOutcome(
                            fail(offerEvaluationStage,
                                    "Offer has insufficient '%s' to increase reservation with resource id '%s' " +
                                            "(have [%s], need [%s], found additional [%s]): '%s'",
                                    resourceSpec.getName(),
                                    resourceId,
                                    TextFormat.shortDebugString(currentReservedValue),
                                    TextFormat.shortDebugString(resourceSpec.getValue()),
                                    availableValue.isPresent()
                                            ? TextFormat.shortDebugString(availableValue.get()) : "NULL",
                                    resourceSpec)
                                    .build(),
                            Optional.empty());
                }

                // Reservation of additional resources.
                // - Reserve recommendation: a resource representing the amount to add to the reservation
                // - Launch task: a resource with the new resulting value
                // For example, when growing a task from 0.7 to 1.0 CPUs, we should have:
                // - Reserve 0.3 CPUs
                // - Task resource with 1.0 CPUs
                return new ReserveEvaluationOutcome(
                        pass(
                                offerEvaluationStage,
                                Arrays.asList(new ReserveOfferRecommendation(
                                        mesosResourcePool.getOffer(),
                                        resourceBuilder
                                                .setValue(mesosResourceToReserveOptional.get().getValue())
                                                .build())),
                                "Offer contains sufficient '%s' to increase reservation with resource id '%s' " +
                                        "by [%s] (need [%s], have [%s]): '%s'",
                                resourceSpec.getName(),
                                resourceId,
                                TextFormat.shortDebugString(mesosResourceToReserveOptional.get().getValue()),
                                TextFormat.shortDebugString(resourceSpec.getValue()),
                                TextFormat.shortDebugString(currentReservedValue),
                                resourceSpec)
                                .build(),
                        Optional.of(resourceBuilder.setValue(resourceSpec.getValue()).build()));
            } else {
                LOGGER.info("    Reservation for resource '{}' needs decreasing from current [{}] to required [{}]",
                        resourceSpec.getName(),
                        TextFormat.shortDebugString(currentReservedValue),
                        TextFormat.shortDebugString(resourceSpec.getValue()));

                // Unreservation of no longer needed resources.
                // - Unreserve recommendation: a resource representing the amount to subtract from the reservation
                // - Launch task: a resource with the new resulting value
                // For example, when shrinking a task from 1.0 to 0.7 CPUs, we should have:
                // - Unreserve 0.3 CPUs
                // - Task resource with 0.7 CPUs
                Protos.Value amountToUnreserve = ValueUtils.subtract(currentReservedValue, resourceSpec.getValue());
                return new ReserveEvaluationOutcome(
                        pass(
                                offerEvaluationStage,
                                Arrays.asList(new UnreserveOfferRecommendation(
                                        mesosResourcePool.getOffer(),
                                        resourceBuilder.setValue(amountToUnreserve).build())),
                                "Decreased reservation of '%s' with resource id '%s' " +
                                        "by [%s] (need [%s], have [%s]): '%s'",
                                resourceSpec.getName(),
                                resourceId,
                                TextFormat.shortDebugString(amountToUnreserve),
                                TextFormat.shortDebugString(resourceSpec.getValue()),
                                TextFormat.shortDebugString(currentReservedValue),
                                resourceSpec)
                                .build(),
                        Optional.of(resourceBuilder.setValue(resourceSpec.getValue()).build()));
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
