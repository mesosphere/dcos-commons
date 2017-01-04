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
    // Resources frequently have necessary task affinities -- a dynamic port, for example, will need to modify the
    // TaskInfo it belongs to. We express that affinity with this field, which if left null means that this evaluation
    // stage modifies the ExecutorInfo instead.
    private String taskName;

    public ResourceEvaluationStage(Resource resource, String taskName) {
        this.resourceRequirement = new ResourceRequirement(resource);
        this.taskName = taskName;
    }

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
            MesosResourcePool offerResourcePool,
            OfferRequirement offerRequirement,
            OfferRecommendationSlate offerRecommendationSlate) throws OfferEvaluationException {
        ResourceRequirement resourceRequirement = getResourceRequirement();
        Optional<MesosResource> mesosResourceOptional = offerResourcePool.consume(resourceRequirement);
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
            if (expectedValueChanged(mesosResource) && !mesosResource.isAtomic()) {
                Value reserveValue = ValueUtils.subtract(resourceRequirement.getValue(), mesosResource.getValue());

                if (ValueUtils.compare(reserveValue, ValueUtils.getZero(reserveValue.getType())) > 0) {
                    logger.info("Updates reserved resource with additional reservation");
                    Resource reserveResource = ResourceUtils.getDesiredResource(
                            resourceRequirement.getRole(),
                            resourceRequirement.getPrincipal(),
                            resourceRequirement.getName(),
                            reserveValue);

                    if (offerResourcePool.consume(new ResourceRequirement(reserveResource)).isPresent()) {
                        reserveResource = ResourceUtils.setResourceId(
                                reserveResource, resourceRequirement.getResourceId());
                        offerRecommendationSlate.addReserveRecommendation(
                                new ReserveOfferRecommendation(offerResourcePool.getOffer(), reserveResource));
                        fulfilledResource = getFulfilledResource(new MesosResource(resourceRequirement.getResource()));
                    } else {
                        throw new OfferEvaluationException("Insufficient resources to increase resource usage.");
                    }
                }
            }
        } else if (resourceRequirement.reservesResource()) {
            logger.info("Reserves Resource");
            offerRecommendationSlate.addReserveRecommendation(
                    new ReserveOfferRecommendation(offerResourcePool.getOffer(), fulfilledResource));
        }

        logger.info("Satisfying resource requirement: {}\nwith resource: {}",
                TextFormat.shortDebugString(resourceRequirement.getResource()),
                TextFormat.shortDebugString(mesosResource.getResource()));

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
