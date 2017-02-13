package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
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
    private static final Logger logger = LoggerFactory.getLogger(ResourceEvaluationStage.class);

    private ResourceRequirement resourceRequirement;
    private final String taskName;

    /**
     * Instantiate this class to check incoming offers for sufficient presence of the supplied {@link Resource}. The
     * supplied task name indicates which task in the {@link OfferRequirement} to update with any subsequent metadata.
     * If it is null, this stage will modify the {@link org.apache.mesos.Protos.ExecutorInfo} instead.
     *
     * @param resource the resource to evaluate incoming offers against
     * @param taskName the name of the task to modify with resource metadata
     */
    public ResourceEvaluationStage(Resource resource, String taskName) {
        this.resourceRequirement = new ResourceRequirement(resource);
        this.taskName = taskName;
    }

    /**
     * Instantiate this class to check incoming offers for sufficient presence of the supplied {@link Resource}. The
     * {@link org.apache.mesos.Protos.ExecutorInfo} on the {@link OfferRequirement} will be modified with any subsequent
     * metadata.
     *
     * @param resource the resource to evaluate incoming offers against
     */
    public ResourceEvaluationStage(Resource resource) {
        this(resource, null);
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
        final String resourceId = resourceRequirement.getResourceId();

        Resource fulfilledResource = getFulfilledResource(resourceRequirement.getResource());
        OfferRecommendation offerRecommendation = null;
        if (resourceRequirement.expectsResource()) {
            logger.info("Expects Resource");

            Optional<MesosResource> existingResourceOptional = mesosResourcePool.getReservedResourceById(resourceId);
            if (!existingResourceOptional.isPresent()) {
                return fail(this, "Expected existing resource is not present in the offer '%s': %s",
                        resourceRequirement.getName(),
                        TextFormat.shortDebugString(resourceRequirement.getResource()));
            }

            Optional<MesosResource> consumedResourceOptional = mesosResourcePool.consume(resourceRequirement);
            if (!consumedResourceOptional.isPresent()) {
                return fail(this, "Failed to satisfy required resource '%s': %s",
                        resourceRequirement.getName(),
                        TextFormat.shortDebugString(resourceRequirement.getResource()));
            }

            MesosResource existingResource = existingResourceOptional.get();

            // Compute any needed resource pool consumption/release operations as well as any additional needed Mesos
            // Operations. In the case where a requirement has changed for an atomic resource, no Operations can be
            // performed because the resource is atomic.

            // Does existing resource satisfy the resource requirement?
            if (ValueUtils.equal(existingResource.getValue(), resourceRequirement.getValue())) {
                logger.info("    Current reservation for resource '{}' matches required value: {}",
                        resourceRequirement.getName(),
                        TextFormat.shortDebugString(existingResource.getValue()),
                        TextFormat.shortDebugString(resourceRequirement.getValue()));
            } else if (resourceRequirement.isAtomic()) {
                logger.info("    Resource '{}' is atomic and cannot be resized from current {} to required {}",
                        resourceRequirement.getName(),
                        TextFormat.shortDebugString(existingResource.getValue()),
                        TextFormat.shortDebugString(resourceRequirement.getValue()));
            } else {
                Value reserveValue = ValueUtils.subtract(resourceRequirement.getValue(), existingResource.getValue());
                if (ValueUtils.compare(reserveValue, ValueUtils.getZero(reserveValue.getType())) > 0) {
                    logger.info("    Reservation for resource '{}' needs increasing from current {} to required {}",
                            resourceRequirement.getName(),
                            TextFormat.shortDebugString(existingResource.getValue()),
                            TextFormat.shortDebugString(resourceRequirement.getValue()));
                    Resource reserveResource = ResourceUtils.getExpectedResource(
                            resourceRequirement.getRole(),
                            resourceRequirement.getPrincipal(),
                            resourceRequirement.getName(),
                            reserveValue);

                    if (mesosResourcePool.consume(new ResourceRequirement(reserveResource)).isPresent()) {
                        reserveResource = ResourceUtils.setResourceId(reserveResource, resourceId);
                        offerRecommendation = new ReserveOfferRecommendation(
                                mesosResourcePool.getOffer(), reserveResource);
                        fulfilledResource = getFulfilledResource(resourceRequirement.getResource());
                    } else {
                        return fail(this, "Insufficient resources to increase reservation of resource '%s'.",
                                resourceRequirement.getName());
                    }
                }
            }
        } else if (resourceRequirement.reservesResource()) {
            logger.info("    Resource '{}' requires a RESERVE operation", resourceRequirement.getName());
            Optional<MesosResource> consumedResourceOptional = mesosResourcePool.consume(resourceRequirement);
            if (!consumedResourceOptional.isPresent()) {
                return fail(this, "Failed to satisfy required resource '%s': %s",
                        resourceRequirement.getName(),
                        TextFormat.shortDebugString(resourceRequirement.getResource()));
            }
            offerRecommendation = new ReserveOfferRecommendation(mesosResourcePool.getOffer(), fulfilledResource);
        }

        logger.info("  Generated '{}' resource for task: [{}]",
                resourceRequirement.getName(), TextFormat.shortDebugString(fulfilledResource));

        EvaluationOutcome failure = validateRequirements(podInfoBuilder.getOfferRequirement());
        if (failure != null) {
            return failure;
        }

        setProtos(podInfoBuilder, fulfilledResource);

        return pass(
                this,
                offerRecommendation == null ? null : Arrays.asList(offerRecommendation),
                "Offer contains sufficient '%s'",
                resourceRequirement.getName());
    }

    protected EvaluationOutcome validateRequirements(OfferRequirement offerRequirement) {
        if (!getTaskName().isPresent() && offerRequirement.getExecutorRequirementOptional().isPresent()) {
            Protos.ExecutorID executorID = offerRequirement.getExecutorRequirementOptional()
                    .get()
                    .getExecutorInfo()
                    .getExecutorId();
            if (!executorID.getValue().isEmpty() && getResourceRequirement().reservesResource()) {
                return fail(this, "When using an existing Executor, no new resources may be required.");
            }
        }
        return null;
    }

    protected Resource getFulfilledResource(Resource resource) {
        Resource.Builder builder = resource.toBuilder().setRole(getResourceRequirement().getRole());
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
                    .newBuilder(resourceRequirement.getResource().getReservation())
                    .setLabels(ResourceUtils.setResourceId(
                            resourceRequirement.getResource().getReservation().getLabels(),
                            UUID.randomUUID().toString()))
                    .build());
        }
    }
}
