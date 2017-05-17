package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.fail;
import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class evaluates an offer against a given {@link OfferRequirement}, ensuring that it contains a sufficient amount
 * or value of the supplied {@link Resource}, and creating a {@link ReserveOfferRecommendation} or
 * {@link UnreserveOfferRecommendation} where necessary.
 */
public class ResourceEvaluationStage implements OfferEvaluationStage {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceEvaluationStage.class);

    private ResourceRequirement resourceRequirement;
    private final String taskName;

    /**
     * Instantiate this class to check incoming offers for sufficient presence of the supplied {@link Resource}. The
     * supplied task name indicates which task in the {@link OfferRequirement} to update with any subsequent metadata.
     * If it is null, this stage will modify the {@link org.apache.mesos.Protos.ExecutorInfo} instead.
     *
     * @param resourceRequirement the resource to evaluate incoming offers against
     * @param taskName the name of the task to modify with resource metadata
     */
    public ResourceEvaluationStage(ResourceRequirement resourceRequirement, String taskName) {
        this.resourceRequirement = resourceRequirement;
        this.taskName = taskName;
    }

    protected void setResourceRequirement(ResourceRequirement resourceRequirement) {
        this.resourceRequirement = resourceRequirement;
    }

    protected ResourceRequirement getResourceRequirement() {
        return resourceRequirement;
    }

    protected Optional<String> getTaskName() {
        return Optional.ofNullable(taskName);
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        final ResourceRequirement resourceRequirement = getResourceRequirement();


        Optional<MesosResource> mesosResourceOptional = resourceRequirement.satisfy(mesosResourcePool);
        if (!mesosResourceOptional.isPresent()) {
            return fail(this, "Failed to satisfy required resource '%s'", resourceRequirement);
        }

        OfferRecommendation offerRecommendation = null;
        MesosResource mesosResource = mesosResourceOptional.get();
        Resource fulfilledResource = getFulfilledResource(resourceRequirement);

        if (ValueUtils.equal(mesosResource.getValue(), resourceRequirement.getValue())) {
            LOGGER.info("    Resource '{}' matches required value: {}",
                    resourceRequirement.getName(),
                    TextFormat.shortDebugString(mesosResource.getValue()),
                    TextFormat.shortDebugString(resourceRequirement.getValue()));

            if (resourceRequirement.reservesResource()) {
                // Initial reservation of resources
                LOGGER.info("    Resource '{}' requires a RESERVE operation", resourceRequirement.getName());
                offerRecommendation = new ReserveOfferRecommendation(mesosResourcePool.getOffer(), fulfilledResource);
            }
        } else {
            Value difference = ValueUtils.subtract(resourceRequirement.getValue(), mesosResource.getValue());
            if (ValueUtils.compare(difference, ValueUtils.getZero(difference.getType())) > 0) {
                LOGGER.info("    Reservation for resource '{}' needs increasing from current {} to required {}",
                        resourceRequirement.getName(),
                        TextFormat.shortDebugString(mesosResource.getValue()),
                        TextFormat.shortDebugString(resourceRequirement.getValue()));

                ResourceRequirement additionalResourceRequirment = ResourceRequirement.newBuilder(
                        resourceRequirement.getRole(),
                        resourceRequirement.getName(),
                        difference)
                        .build();
                mesosResourceOptional = additionalResourceRequirment.satisfy(mesosResourcePool);
                if (!mesosResourceOptional.isPresent()) {
                    return fail(
                            this,
                            "Insufficient resources to increase reservation of resource '%s'.",
                            resourceRequirement.getName());
                }

                mesosResource = mesosResourceOptional.get();

                // Reservation of additional resources
                offerRecommendation = new ReserveOfferRecommendation(
                        mesosResourcePool.getOffer(),
                        mesosResource.getResource());
            } else {
                LOGGER.info("    Reservation for resource '{}' needs decreasing from current {} to required {}",
                        resourceRequirement.getName(),
                        TextFormat.shortDebugString(mesosResource.getValue()),
                        TextFormat.shortDebugString(resourceRequirement.getValue()));
                Value unreserve = ValueUtils.subtract(mesosResource.getValue(), resourceRequirement.getValue());

                // Unreservation of no longer needed resources
                offerRecommendation = new UnreserveOfferRecommendation(
                        mesosResourcePool.getOffer(),
                        ResourceUtils.setValue(mesosResource.getResource(), unreserve));
            }
        }

        setProtos(podInfoBuilder, fulfilledResource);

        return pass(
                this,
                offerRecommendation == null ? Collections.emptyList() : Arrays.asList(offerRecommendation),
                "Offer contains sufficient '%s': requirement=%s",
                resourceRequirement.getName(),
                TextFormat.shortDebugString(resourceRequirement.getValue()));
    }

    protected Resource getFulfilledResource(ResourceRequirement resourceRequirement) {
        // TODO: Correctly generate fulfilled resource
        Resource.Builder builder = Resource.newBuilder().setRole(getResourceRequirement().getRole());
        Optional<Resource.ReservationInfo> reservationInfo = getFulfilledReservationInfo();
        if (reservationInfo.isPresent()) {
            builder.setReservation(reservationInfo.get());
        }

        return builder.build();
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

    protected Optional<Resource.ReservationInfo> getFulfilledReservationInfo() {
        ResourceRequirement resourceRequirement = getResourceRequirement();

        if (!resourceRequirement.reservesResource()) {
            return Optional.empty();
        } else {
            return Optional.of(Resource.ReservationInfo
                    .newBuilder()
                    .setLabels(
                            Protos.Labels.newBuilder()
                                    .addLabels(
                                            Protos.Label.newBuilder()
                                                    .setKey(MesosResource.RESOURCE_ID_KEY)
                                                    .setValue(UUID.randomUUID().toString())))
                    .build());
        }
    }
}
