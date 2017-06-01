package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.fail;
import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement},
 * ensuring that it contains an appropriately-sized volume, and creating any necessary instances of
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
        List<OfferRecommendation> offerRecommendations = new ArrayList<>();

        Resource resource;
        if (volumeSpec.getType().equals(VolumeSpec.Type.ROOT)) {
            EvaluationOutcome evaluationOutcome = OfferEvaluationUtils.evaluateSimpleResource(
                    this,
                    resourceSpec,
                    resourceId,
                    mesosResourcePool);
            if (!evaluationOutcome.isPassing()) {
                return evaluationOutcome;
            }

            offerRecommendations.addAll(evaluationOutcome.getOfferRecommendations());
            resource = ResourceBuilder.fromSpec(volumeSpec, resourceId, persistenceId, Optional.empty()).build();
        } else {
            Optional<MesosResource> mesosResourceOptional = Optional.empty();
            if (!resourceId.isPresent()) {
                mesosResourceOptional =
                        mesosResourcePool.consumeAtomic(Constants.DISK_RESOURCE_TYPE, volumeSpec.getValue());
            } else {
                mesosResourceOptional =
                        mesosResourcePool.getReservedResourceById(resourceId.get());
            }

            if (!mesosResourceOptional.isPresent()) {
                return fail(this, "Failed to find MOUNT volume for '%s'.", getSummary());
            }

            MesosResource mesosResource = mesosResourceOptional.get();
            resource = ResourceBuilder.fromSpec(
                    volumeSpec,
                    resourceId,
                    persistenceId,
                    Optional.of(mesosResource.getResource().getDisk().getSource().getMount().getRoot()))
                    .setValue(mesosResource.getValue())
                    .build();

            if (!resourceId.isPresent()) {
                // Initial reservation of resources
                logger.info("    Resource '{}' requires a RESERVE operation", resourceSpec.getName());
                offerRecommendations.add(new ReserveOfferRecommendation(
                        mesosResourcePool.getOffer(),
                        resource));
            }
        }

        if (createsVolume()) {
            logger.info("    Resource '{}' requires a CREATE operation", volumeSpec.getName());
            offerRecommendations.add(new CreateOfferRecommendation(mesosResourcePool.getOffer(), resource));
        }

        logger.info("  Generated '{}' resource for task: [{}]",
                volumeSpec.getName(), TextFormat.shortDebugString(resource));
        super.setProtos(podInfoBuilder, resource);

        return pass(
                this,
                offerRecommendations,
                "Satisfied requirements for %s volume '%s'",
                volumeSpec.getType(),
                volumeSpec.getContainerPath());
    }
}
