package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.VolumeSpec;
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
    private final VolumeSpec volumeSpec;
    private final Optional<String> persistenceId;

    public VolumeEvaluationStage(
            VolumeSpec volumeSpec,
            String taskName,
            Optional<String> resourceId,
            Optional<String> persistenceId) {
        super(volumeSpec, resourceId, taskName);
        this.volumeSpec = volumeSpec;
        this.persistenceId = persistenceId;
    }

    private boolean createsVolume() {
        return !persistenceId.isPresent();
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        Optional<MesosResource> mesosResourceOptional = consume(volumeSpec, mesosResourcePool);

        if (!mesosResourceOptional.isPresent()) {
            return fail(this, "Insufficient resources for disk for volume '%s'", volumeSpec.getContainerPath());
        }

        List<OfferRecommendation> offerRecommendations = new ArrayList<>();
        Resource fulfilledResource = getFulfilledResource(volumeSpec, mesosResourceOptional.get());

        if (reservesResource()) {
            logger.info("    Resource '{}' requires a RESERVE operation", volumeSpec.getName());
            offerRecommendations.add(
                    new ReserveOfferRecommendation(mesosResourcePool.getOffer(), fulfilledResource));
        }

        if (createsVolume()) {
            logger.info("    Resource '{}' requires a CREATE operation", volumeSpec.getName());
            offerRecommendations.add(new CreateOfferRecommendation(mesosResourcePool.getOffer(), fulfilledResource));
        }

        logger.info("  Generated '{}' resource for task: [{}]",
                volumeSpec.getName(), TextFormat.shortDebugString(fulfilledResource));

        setProtos(podInfoBuilder, fulfilledResource);

        return pass(
                this,
                offerRecommendations,
                "Satisfied requirements for %s volume '%s'",
                volumeSpec.getType(),
                volumeSpec.getContainerPath());
    }

    protected Resource getFulfilledResource(VolumeSpec volumeSpec, MesosResource mesosResource) {
        // TODO: Correctly generate fulfilled resource
        Resource.Builder builder = super.getFulfilledResource().toBuilder();
        String persistenceId = createsVolume() ?
                UUID.randomUUID().toString() :
                this.persistenceId.get();
        DiskInfo diskInfo = builder.getDisk().toBuilder()
                .setPersistence(DiskInfo.Persistence.newBuilder()
                        .setId(persistenceId))
                .build();
        builder.setDisk(diskInfo);

        return builder.build();
    }
}
