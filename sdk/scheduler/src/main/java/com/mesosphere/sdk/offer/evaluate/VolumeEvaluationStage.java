package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.offer.OfferRequirement}, ensuring that it
 * contains an appropriately-sized volume, and creating any necessary instances of
 * {@link com.mesosphere.sdk.offer.ReserveOfferRecommendation} and
 * {@link com.mesosphere.sdk.offer.CreateOfferRecommendation} as necessary.
 */
public class VolumeEvaluationStage extends ResourceEvaluationStage implements OfferEvaluationStage {
    private static final Logger logger = LoggerFactory.getLogger(VolumeEvaluationStage.class);

    public VolumeEvaluationStage(Resource resource, String taskName) {
        super(resource, taskName);
    }

    public VolumeEvaluationStage(Resource resource) {
        super(resource);
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

        if (resourceRequirement.reservesResource()) {
            logger.info("    Resource '{}' requires a RESERVE operation", resourceRequirement.getName());
            offerRecommendationSlate.addReserveRecommendation(
                    new ReserveOfferRecommendation(mesosResourcePool.getOffer(), fulfilledResource));
        }

        if (resourceRequirement.createsVolume()) {
            logger.info("    Resource '{}' requires a CREATE operation", resourceRequirement.getName());
            offerRecommendationSlate.addCreateRecommendation(
                    new CreateOfferRecommendation(mesosResourcePool.getOffer(), fulfilledResource));
        }

        logger.info("  Generated '{}' resource for task: [{}]",
                resourceRequirement.getName(), TextFormat.shortDebugString(fulfilledResource));

        validateRequirements(offerRequirement);
        setProtos(offerRequirement, fulfilledResource);
    }

    @Override
    protected Protos.Resource getFulfilledResource(MesosResource mesosResource) {
        Resource.Builder builder = super.getFulfilledResource(mesosResource).toBuilder();
        Optional<DiskInfo> diskInfo = getFulfilledDiskInfo(mesosResource);

        if (diskInfo.isPresent()) {
            builder.setDisk(diskInfo.get());
        }

        return builder.build();
    }

    private Optional<DiskInfo> getFulfilledDiskInfo(MesosResource mesRes) {
        ResourceRequirement resourceRequirement = getResourceRequirement();
        if (!resourceRequirement.getResource().hasDisk()) {
            return Optional.empty();
        }

        DiskInfo.Builder builder = DiskInfo.newBuilder(resourceRequirement.getResource().getDisk());
        if (mesRes.getResource().getDisk().hasSource()) {
            builder.setSource(mesRes.getResource().getDisk().getSource());
        }

        Optional<DiskInfo.Persistence> persistence = getFulfilledPersistence();
        if (persistence.isPresent()) {
            builder.setPersistence(persistence.get());
        }

        return Optional.of(builder.build());
    }

    private Optional<DiskInfo.Persistence> getFulfilledPersistence() {
        ResourceRequirement resourceRequirement = getResourceRequirement();
        if (!resourceRequirement.createsVolume()) {
            return Optional.empty();
        } else {
            return Optional.of(DiskInfo.Persistence
                    .newBuilder(resourceRequirement.getResource().getDisk().getPersistence())
                    .setId(UUID.randomUUID().toString())
                    .build());
        }
    }
}
