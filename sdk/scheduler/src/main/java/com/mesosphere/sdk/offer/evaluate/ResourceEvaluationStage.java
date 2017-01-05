package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

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
    public void evaluate(
            MesosResourcePool mesosResourcePool,
            OfferRequirement offerRequirement,
            OfferRecommendationSlate offerRecommendationSlate) throws OfferEvaluationException {
        ResourceRequirement resourceRequirement = getResourceRequirement();
        Optional<MesosResource> mesosResourceOptional = mesosResourcePool.consume(resourceRequirement);
        if (!mesosResourceOptional.isPresent()) {
            throw new OfferEvaluationException(String.format(
                    "Failed to satisfy resource requirement: %s",
                    TextFormat.shortDebugString(resourceRequirement.getResource())));
        }

        final MesosResource mesosResource = mesosResourceOptional.get();
        Resource fulfilledResource = getFulfilledResource(mesosResource);
        if (resourceRequirement.expectsResource()) {
            logger.info("Expects Resource");

            // Compute any needed resource pool consumption / release operations
            // as well as any additional needed Mesos Operations.  In the case
            // where a requirement has changed for an Atomic resource, no Operations
            // can be performed because the resource is Atomic.
            if (!expectedValueChanged(mesosResource)) {
                logger.info("    Current reservation for resource '{}' matches required value: {}",
                        resourceRequirement.getName(),
                        TextFormat.shortDebugString(mesosResource.getValue()),
                        TextFormat.shortDebugString(resourceRequirement.getValue()));
            } else if (mesosResource.isAtomic()) {
                logger.info("    Resource '{}' is atomic and cannot be resized from current {} to required {}",
                        resourceRequirement.getName(),
                        TextFormat.shortDebugString(mesosResource.getValue()),
                        TextFormat.shortDebugString(resourceRequirement.getValue()));
            } else {
                Value reserveValue = ValueUtils.subtract(resourceRequirement.getValue(), mesosResource.getValue());

                if (ValueUtils.compare(reserveValue, ValueUtils.getZero(reserveValue.getType())) > 0) {
                    logger.info("    Reservation for resource '{}' needs increasing from current {} to required {}",
                            resourceRequirement.getName(),
                            TextFormat.shortDebugString(mesosResource.getValue()),
                            TextFormat.shortDebugString(resourceRequirement.getValue()));
                    Resource reserveResource = ResourceUtils.getDesiredResource(
                            resourceRequirement.getRole(),
                            resourceRequirement.getPrincipal(),
                            resourceRequirement.getName(),
                            reserveValue);

                    if (mesosResourcePool.consume(new ResourceRequirement(reserveResource)).isPresent()) {
                        reserveResource = ResourceUtils.setResourceId(
                                reserveResource, resourceRequirement.getResourceId());
                        offerRecommendationSlate.addReserveRecommendation(
                                new ReserveOfferRecommendation(mesosResourcePool.getOffer(), reserveResource));
                        fulfilledResource = getFulfilledResource(new MesosResource(resourceRequirement.getResource()));
                    } else {
                        throw new OfferEvaluationException("Insufficient resources to increase resource usage.");
                    }
                }
            }
        } else if (resourceRequirement.reservesResource()) {
            logger.info("    Resource '{}' requires a RESERVE operation", resourceRequirement.getName());
            offerRecommendationSlate.addReserveRecommendation(
                    new ReserveOfferRecommendation(mesosResourcePool.getOffer(), fulfilledResource));
        }

        logger.info("  Generated '{}' resource for task: [{}]",
                resourceRequirement.getName(), TextFormat.shortDebugString(fulfilledResource));

        validateRequirements(offerRequirement);
        setProtos(offerRequirement, fulfilledResource);
    }

    protected void validateRequirements(OfferRequirement offerRequirement) throws OfferEvaluationException {
        if (!getTaskName().isPresent() && offerRequirement.getExecutorRequirementOptional().isPresent()) {
            Protos.ExecutorID executorID = offerRequirement.getExecutorRequirementOptional()
                    .get()
                    .getExecutorInfo()
                    .getExecutorId();
            if (!executorID.getValue().isEmpty() && getResourceRequirement().reservesResource()) {
                throw new OfferEvaluationException(
                        "When using an existing Executor, no new resources may be required.");
            }
        }
    }

    protected void setProtos(OfferRequirement offerRequirement, Resource resource) {
        if (getTaskName().isPresent()) {
            offerRequirement.updateTaskRequirement(
                    getTaskName().get(),
                    ResourceUtils.setResource(
                            offerRequirement.getTaskRequirement(getTaskName().get()).getTaskInfo().toBuilder(),
                            resource).build());
        } else {
            Protos.ExecutorInfo executorInfo = offerRequirement.getExecutorRequirementOptional()
                    .get().getExecutorInfo();
            offerRequirement.updateExecutorRequirement(
                    ResourceUtils.setResource(executorInfo.toBuilder(), resource).build());
        }
    }

    protected Resource getFulfilledResource(MesosResource mesosResource) {
        ResourceRequirement resourceRequirement = getResourceRequirement();
        Resource.Builder builder = Resource.newBuilder(mesosResource.getResource());
        builder.setRole(resourceRequirement.getResource().getRole());

        Optional<Resource.ReservationInfo> resInfo = getFulfilledReservationInfo();
        if (resInfo.isPresent()) {
            builder.setReservation(resInfo.get());
        }

        return builder.build();
    }

    private Optional<Resource.ReservationInfo> getFulfilledReservationInfo() {
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

    private boolean expectedValueChanged(MesosResource mesosResource) {
        return !ValueUtils.equal(getResourceRequirement().getValue(), mesosResource.getValue());
    }
}
