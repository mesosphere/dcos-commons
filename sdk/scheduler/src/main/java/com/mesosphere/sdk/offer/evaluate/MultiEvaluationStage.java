package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ReserveOfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * This class evaluates an offer across zero or more child evaluation stages for a single resource type.
 */
public class MultiEvaluationStage implements OfferEvaluationStage {
    private final Collection<OfferEvaluationStage> childEvaluationStages;

    public MultiEvaluationStage(Collection<OfferEvaluationStage> childEvaluationStages) {
        this.childEvaluationStages = childEvaluationStages;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        Collection<EvaluationOutcome> childOutcomes = childEvaluationStages.stream()
                .map(stage -> stage.evaluate(mesosResourcePool, podInfoBuilder))
                .collect(Collectors.toList());
        boolean allPassing = childOutcomes.stream().allMatch(outcome -> outcome.isPassing());
        Collection<OfferRecommendation> recommendations = childOutcomes.stream()
                .map(o -> o.getOfferRecommendations())
                .flatMap(xs -> xs.stream())
                .collect(Collectors.toList());

        return EvaluationOutcome.create(
                allPassing,
                this,
                recommendations.isEmpty()
                        ? Collections.emptyList()
                        : Arrays.asList(coalesceRangeRecommendations(recommendations)),
                childOutcomes.stream() // Ensure child recommendations don't duplicate our coalesced recommendation
                        .map(outcome -> EvaluationOutcome.create(
                                outcome.isPassing(),
                                this,
                                Collections.emptyList(),
                                Collections.emptyList(),
                                outcome.getReason())).collect(Collectors.toList()),
                allPassing ? "All child stages passed" : "Failed to pass all child stages");
    }

    private static OfferRecommendation coalesceRangeRecommendations(Collection<OfferRecommendation> recommendations) {
        Protos.Resource mergedResource = null;
        Protos.Offer offer = null;
        for (OfferRecommendation recommendation : recommendations) {
            Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
            if (offer == null) {
                offer = recommendation.getOffer();
            }

            if (mergedResource == null) {
                mergedResource = resource;
            } else {
                mergedResource = ResourceUtils.mergeRanges(mergedResource, resource);
            }
        }

        return new ReserveOfferRecommendation(offer, mergedResource);
    }
}
