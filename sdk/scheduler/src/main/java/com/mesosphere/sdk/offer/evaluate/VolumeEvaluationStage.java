package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.*;

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
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        ResourceRequirement resourceRequirement = getResourceRequirement();
        Optional<MesosResource> mesosResourceOptional = mesosResourcePool.consume(resourceRequirement);
        if (!mesosResourceOptional.isPresent()) {
            return fail(this, "Failed to satisfy requirements for %s volume '%s': %s",
                    getVolumeType(),
                    resourceRequirement.getResource().getDisk().getVolume().getContainerPath(),
                    TextFormat.shortDebugString(resourceRequirement.getResource()));
        }

        final MesosResource mesosResource = mesosResourceOptional.get();
        Resource fulfilledResource = toFulfilledResource(mesosResource.getResource());
        Collection<OfferRecommendation> offerRecommendations = new ArrayList<>();

        if (resourceRequirement.reservesResource()) {
            logger.info("    Resource '{}' requires a RESERVE operation", resourceRequirement.getName());
            offerRecommendations.add(new ReserveOfferRecommendation(mesosResourcePool.getOffer(), fulfilledResource));
        }

        if (resourceRequirement.createsVolume()) {
            logger.info("    Resource '{}' requires a CREATE operation", resourceRequirement.getName());
            offerRecommendations.add(new CreateOfferRecommendation(mesosResourcePool.getOffer(), fulfilledResource));
        }

        logger.info("  Generated '{}' resource for task: [{}]",
                resourceRequirement.getName(), TextFormat.shortDebugString(fulfilledResource));

        EvaluationOutcome failure = validateRequirements(podInfoBuilder.getOfferRequirement());
        if (failure != null) {
            return failure;
        }

        setProtos(podInfoBuilder, fulfilledResource);

        return pass(this, offerRecommendations,
                "Satisfied requirements for %s volume '%s'",
                getVolumeType(),
                resourceRequirement.getResource().getDisk().getVolume().getContainerPath());
    }

    @Override
    protected Resource toFulfilledResource(Resource consumedResource) {
        Resource fulfilledResource = super.toFulfilledResource(consumedResource);

        //TODO(nickbp): use ResourceBuilder for this?
        Optional<DiskInfo> diskInfo = getFulfilledDiskInfo(consumedResource);
        if (diskInfo.isPresent()) {
            fulfilledResource = fulfilledResource.toBuilder()
                    .setDisk(diskInfo.get())
                    .build();
        }

        return fulfilledResource;
    }

    private Optional<DiskInfo> getFulfilledDiskInfo(Resource consumedResource) {
        ResourceRequirement resourceRequirement = getResourceRequirement();
        if (!resourceRequirement.getResource().hasDisk()) {
            return Optional.empty();
        }

        DiskInfo.Builder builder = DiskInfo.newBuilder(resourceRequirement.getResource().getDisk());

        if (consumedResource.getDisk().hasSource()) {
            builder.setSource(consumedResource.getDisk().getSource());
        }

        if (resourceRequirement.createsVolume()) {
            builder.getPersistenceBuilder()
                    .mergeFrom(resourceRequirement.getResource().getDisk().getPersistence())
                    .setId(UUID.randomUUID().toString());
        }

        return Optional.of(builder.build());
    }

    private String getVolumeType() {
        return getResourceRequirement().getResource().getDisk().hasSource() ? "MOUNT" : "ROOT";
    }
}
