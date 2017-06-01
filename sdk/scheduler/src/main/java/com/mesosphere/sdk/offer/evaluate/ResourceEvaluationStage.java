package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement},
 * ensuring that it contains a sufficient amount or value of the supplied {@link Resource}, and creating a
 * {@link ReserveOfferRecommendation} or {@link UnreserveOfferRecommendation} where necessary.
 */
public class ResourceEvaluationStage implements OfferEvaluationStage {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected final String taskName;
    protected final Optional<String> resourceId;
    protected ResourceSpec resourceSpec;

    public ResourceEvaluationStage(ResourceSpec resourceSpec, Optional<String> resourceId, String taskName) {
        this.resourceSpec = resourceSpec;
        this.resourceId = resourceId;
        this.taskName = taskName;
    }

    protected Optional<String> getTaskName() {
        return Optional.ofNullable(taskName);
    }

    protected Optional<MesosResource> consume(ResourceSpec resourceSpec, MesosResourcePool pool) {
        if (reservesResource()) {
            return pool.consumeReservableMerged(
                    resourceSpec.getName(),
                    resourceSpec.getValue(),
                    resourceSpec.getPreReservedRole());
        } else {
            return pool.consumeReserved(resourceSpec.getName(), resourceSpec.getValue(), resourceId.get());
        }
    }

    protected boolean reservesResource() {
        return !resourceId.isPresent();
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        IntermediateEvaluationOutcome intermediateOutcome = evaluateInternal(mesosResourcePool, podInfoBuilder);
        if (intermediateOutcome.hasPassed()) {
            setProtos(podInfoBuilder, intermediateOutcome.getResource());
        }

        return intermediateOutcome.toEvaluationOutcome(this);
    }

    protected IntermediateEvaluationOutcome evaluateInternal(
            MesosResourcePool mesosResourcePool,
            PodInfoBuilder podInfoBuilder) {

        Optional<MesosResource> mesosResourceOptional = consume(resourceSpec, mesosResourcePool);
        if (!mesosResourceOptional.isPresent()) {
            return new IntermediateEvaluationOutcome(
                    false,
                    null,
                    Collections.emptyList(),
                    Arrays.asList(String.format("Failed to satisfy required resource '%s'", getSummary())));
        }

        OfferRecommendation offerRecommendation = null;
        MesosResource mesosResource = mesosResourceOptional.get();
        Resource fulfilledResource = getFulfilledResource();

        if (ValueUtils.equal(mesosResource.getValue(), resourceSpec.getValue())) {
            logger.info("    Resource '{}' matches required value: {}",
                    resourceSpec.getName(),
                    TextFormat.shortDebugString(mesosResource.getValue()),
                    TextFormat.shortDebugString(resourceSpec.getValue()));

            if (reservesResource()) {
                // Initial reservation of resources
                logger.info("    Resource '{}' requires a RESERVE operation", resourceSpec.getName());
                offerRecommendation = new ReserveOfferRecommendation(mesosResourcePool.getOffer(), fulfilledResource);
            }
        } else {
            Value difference = ValueUtils.subtract(resourceSpec.getValue(), mesosResource.getValue());
            if (ValueUtils.compare(difference, ValueUtils.getZero(difference.getType())) > 0) {
                logger.info("    Reservation for resource '{}' needs increasing from current {} to required {}",
                        resourceSpec.getName(),
                        TextFormat.shortDebugString(mesosResource.getValue()),
                        TextFormat.shortDebugString(resourceSpec.getValue()));

                ResourceSpec requiredAdditionalResources = DefaultResourceSpec.newBuilder(resourceSpec)
                        .value(difference)
                        .build();
                mesosResourceOptional = mesosResourcePool.consumeReservableMerged(
                        requiredAdditionalResources.getName(),
                        requiredAdditionalResources.getValue(),
                        resourceSpec.getPreReservedRole());

                if (!mesosResourceOptional.isPresent()) {
                    return new IntermediateEvaluationOutcome(
                            false,
                            null,
                            Collections.emptyList(),
                            Arrays.asList(
                                    String.format(
                                            "Insufficient resources to increase reservation of resource '%s'",
                                            getSummary())));
                }

                mesosResource = mesosResourceOptional.get();

                Resource resource = ResourceBuilder.fromExistingResource(getFulfilledResource())
                        .setValue(mesosResource.getValue())
                        .build();
                // Reservation of additional resources
                offerRecommendation = new ReserveOfferRecommendation(
                        mesosResourcePool.getOffer(),
                        resource);
            } else {
                logger.info("    Reservation for resource '{}' needs decreasing from current {} to required {}",
                        resourceSpec.getName(),
                        TextFormat.shortDebugString(mesosResource.getValue()),
                        TextFormat.shortDebugString(resourceSpec.getValue()));

                Value unreserve = ValueUtils.subtract(mesosResource.getValue(), resourceSpec.getValue());
                Resource resource = ResourceBuilder.fromExistingResource(getFulfilledResource())
                        .setValue(unreserve)
                        .build();
                // Unreservation of no longer needed resources
                offerRecommendation = new UnreserveOfferRecommendation(
                        mesosResourcePool.getOffer(),
                        resource);
            }
        }

        List<OfferRecommendation> recommendations = new ArrayList<>();
        if (offerRecommendation != null) {
            recommendations.add(offerRecommendation);
        }

        return new IntermediateEvaluationOutcome(
                true,
                fulfilledResource,
                recommendations,
                Arrays.asList(String.format(
                        "Offer contains sufficient '%s': for requirement: '%s'",
                        resourceSpec.getName(),
                        getSummary())));
    }

    protected Resource getFulfilledResource() {
        Resource.Builder builder = ResourceBuilder.fromSpec(resourceSpec).build().toBuilder();
        Optional<Resource.ReservationInfo> reservationInfo = getFulfilledReservationInfo();

        if (reservationInfo.isPresent()) {
            builder.setReservation(reservationInfo.get());
        }
        return builder.build();
    }

    protected Optional<Resource.ReservationInfo> getFulfilledReservationInfo() {
        String resourceId = this.resourceId.isPresent() ? this.resourceId.get() : UUID.randomUUID().toString();

        return Optional.of(Resource.ReservationInfo
                .newBuilder()
                .setPrincipal(resourceSpec.getPrincipal())
                .setLabels(
                        Protos.Labels.newBuilder()
                                .addLabels(
                                        Protos.Label.newBuilder()
                                                .setKey(MesosResource.RESOURCE_ID_KEY)
                                                .setValue(resourceId)))
                .build());
    }

    protected void setProtos(PodInfoBuilder podInfoBuilder, Resource resource) {
        if (getTaskName().isPresent()) {
            Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(getTaskName().get());
            taskBuilder.addResources(resource);
        } else {
            Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
            executorBuilder.addResources(resource);
        }
    }

    protected String getSummary() {
        return String.format(
                "name: '%s', value: '%s', role: '%s', principal: '%s', resourceId: '%s'",
                resourceSpec.getName(),
                TextFormat.shortDebugString(resourceSpec.getValue()),
                resourceSpec.getRole(),
                resourceSpec.getPrincipal(),
                resourceId);
    }
}
