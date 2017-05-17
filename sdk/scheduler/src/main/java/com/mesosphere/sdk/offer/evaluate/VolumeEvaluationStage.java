package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.fail;
import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.offer.OfferRequirement}, ensuring that it
 * contains an appropriately-sized volume, and creating any necessary instances of
 * {@link com.mesosphere.sdk.offer.ReserveOfferRecommendation} and
 * {@link com.mesosphere.sdk.offer.CreateOfferRecommendation} as necessary.
 */
public class VolumeEvaluationStage extends ResourceEvaluationStage {
    private static final Logger logger = LoggerFactory.getLogger(VolumeEvaluationStage.class);
    private final String containerPath;
    private final VolumeRequirement volumeRequirement;

    public VolumeEvaluationStage(VolumeRequirement volumeRequirement, String taskName, String containerPath) {
        super(volumeRequirement, taskName);
        this.volumeRequirement = volumeRequirement;
        this.containerPath = containerPath;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        Optional<MesosResource> mesosResourceOptional = volumeRequirement.satisfy(mesosResourcePool);

        if (!mesosResourceOptional.isPresent()) {
            return fail(this, "Insufficient resources for disk for volume '%s'", containerPath);
        }

        List<OfferRecommendation> offerRecommendations = new ArrayList<>();
        Resource fulfilledResource = getFulfilledResource(volumeRequirement, mesosResourceOptional.get());

        if (volumeRequirement.reservesResource()) {
            logger.info("    Resource '{}' requires a RESERVE operation", volumeRequirement.getName());
            offerRecommendations.add(
                    new ReserveOfferRecommendation(mesosResourcePool.getOffer(), fulfilledResource));
        }

        if (volumeRequirement.createsVolume()) {
            logger.info("    Resource '{}' requires a CREATE operation", volumeRequirement.getName());
            offerRecommendations.add(new CreateOfferRecommendation(mesosResourcePool.getOffer(), fulfilledResource));
        }

        logger.info("  Generated '{}' resource for task: [{}]",
                volumeRequirement.getName(), TextFormat.shortDebugString(fulfilledResource));

        setProtos(podInfoBuilder, fulfilledResource);

        return pass(
                this,
                offerRecommendations,
                "Satisfied requirements for %s volume '%s'",
                volumeRequirement.getDiskType(),
                containerPath);
    }

    protected Resource getFulfilledResource(VolumeRequirement volumeRequirement, MesosResource mesosResource) {
        // TODO: Correctly generate fulfilled resource
        Resource.Builder builder = super.getFulfilledResource(volumeRequirement).toBuilder();
        String persistenceId = volumeRequirement.createsVolume() ?
                UUID.randomUUID().toString() :
                volumeRequirement.getPersistenceId();
        DiskInfo diskInfo = builder.getDisk().toBuilder()
                .setPersistence(DiskInfo.Persistence.newBuilder()
                        .setId(persistenceId))
                .build();
        builder.setDisk(diskInfo);

        return builder.build();
    }
}
