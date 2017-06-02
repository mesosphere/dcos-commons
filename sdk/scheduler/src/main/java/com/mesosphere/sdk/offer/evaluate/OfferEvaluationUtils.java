package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.fail;
import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * Created by gabriel on 6/2/17.
 */
public class OfferEvaluationUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(OfferEvaluationUtils.class);

    public static EvaluationOutcome evaluateSimpleResource (
            ResourceEvaluationStage resourceEvaluationStage,
            ResourceSpec resourceSpec,
            Optional<String> resourceId,
            MesosResourcePool mesosResourcePool) {

        Optional<MesosResource> mesosResourceOptional = consume(resourceSpec, resourceId, mesosResourcePool);
        if (!mesosResourceOptional.isPresent()) {
            return fail(
                    resourceEvaluationStage,
                    "Offer failed to satisfy: %s with resourceId: %s",
                    resourceSpec,
                    resourceId);
        }

        OfferRecommendation offerRecommendation = null;
        MesosResource mesosResource = mesosResourceOptional.get();

        if (ValueUtils.equal(mesosResource.getValue(), resourceSpec.getValue())) {
            LOGGER.info("    Resource '{}' matches required value: {}",
                    resourceSpec.getName(),
                    TextFormat.shortDebugString(mesosResource.getValue()),
                    TextFormat.shortDebugString(resourceSpec.getValue()));

            if (!resourceId.isPresent()) {
                // Initial reservation of resources
                LOGGER.info("    Resource '{}' requires a RESERVE operation", resourceSpec.getName());
                Protos.Resource resource = ResourceBuilder.fromSpec(resourceSpec, resourceId).build();
                resourceEvaluationStage.setResourceId(ResourceCollectionUtils.getResourceId(resource));
                offerRecommendation = new ReserveOfferRecommendation(mesosResourcePool.getOffer(), resource);
            }
        } else {
            Protos.Value difference = ValueUtils.subtract(resourceSpec.getValue(), mesosResource.getValue());
            if (ValueUtils.compare(difference, ValueUtils.getZero(difference.getType())) > 0) {
                LOGGER.info("    Reservation for resource '{}' needs increasing from current {} to required {}",
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
                    return fail(
                            resourceEvaluationStage,
                            "Insufficient resources to increase reservation of resource '%s' with resourceId",
                            resourceSpec,
                            resourceId);
                }

                mesosResource = mesosResourceOptional.get();
                Protos.Resource resource = ResourceBuilder.fromSpec(resourceSpec, resourceId)
                        .setValue(mesosResource.getValue())
                        .build();
                // Reservation of additional resources
                offerRecommendation = new ReserveOfferRecommendation(
                        mesosResourcePool.getOffer(),
                        resource);
            } else {
                LOGGER.info("    Reservation for resource '%s' needs decreasing from current %s to required {}",
                        resourceSpec.getName(),
                        TextFormat.shortDebugString(mesosResource.getValue()),
                        TextFormat.shortDebugString(resourceSpec.getValue()));

                Protos.Value unreserve = ValueUtils.subtract(mesosResource.getValue(), resourceSpec.getValue());
                Protos.Resource resource = ResourceBuilder.fromSpec(resourceSpec, resourceId)
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

        return pass(
                resourceEvaluationStage,
                recommendations,
                "Offer contains sufficient '%s': for resource: '%s' with resourceId: '%s'",
                resourceSpec.getName(),
                resourceSpec,
                resourceId);
    }

    private static Optional<MesosResource> consume(
            ResourceSpec resourceSpec,
            Optional<String> resourceId,
            MesosResourcePool pool) {

        if (!resourceId.isPresent()) {
            return pool.consumeReservableMerged(
                    resourceSpec.getName(),
                    resourceSpec.getValue(),
                    resourceSpec.getPreReservedRole());
        } else {
            return pool.consumeReserved(resourceSpec.getName(), resourceSpec.getValue(), resourceId.get());
        }
    }
}
