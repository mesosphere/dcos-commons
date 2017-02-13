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
 * This class evaluates an offer for its port resources against an {@link com.mesosphere.sdk.offer.OfferRequirement}.
 * Individual port evaluations are handled by the children {@link PortEvaluationStage}s.
 */
public class PortsEvaluationStage implements OfferEvaluationStage {
    private final Collection<OfferEvaluationStage> portEvaluationStages;

    public PortsEvaluationStage(Collection<OfferEvaluationStage> portEvaluationStages) {
        this.portEvaluationStages = portEvaluationStages;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        Collection<EvaluationOutcome> evaluationOutcomes = portEvaluationStages.stream()
                .map(stage -> stage.evaluate(mesosResourcePool, podInfoBuilder))
                .collect(Collectors.toList());
        boolean succeeded = evaluationOutcomes.stream().allMatch(outcome -> outcome.isPassing());
        Collection<OfferRecommendation> recommendations = evaluationOutcomes.stream()
                .map(o -> o.getOfferRecommendations())
                .flatMap(xs -> xs.stream())
                .collect(Collectors.toList());

        return EvaluationOutcome.create(
                succeeded,
                this,
                recommendations.size() > 0 ? Arrays.asList(coalescePortRecommendations(recommendations)) : null,
                evaluationOutcomes.stream()
                        .map(outcome -> EvaluationOutcome.create(
                                outcome.isPassing(),
                                this,
                                null,
                                Collections.emptyList(),
                                outcome.getReason())).collect(Collectors.toList()),
                succeeded ? "Offer contains sufficient 'ports'" : "Failed to satisfy all required ports");
    }

    private OfferRecommendation coalescePortRecommendations(Collection<OfferRecommendation> recommendations) {
        Protos.Resource ports = null;
        Protos.Offer offer = null;
        for (OfferRecommendation recommendation : recommendations) {
            Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
            if (offer == null) {
                offer = recommendation.getOffer();
            }

            if (ports == null) {
                ports = resource;
            } else {
                ports = ResourceUtils.mergeRanges(ports, resource);
            }
        }

        return new ReserveOfferRecommendation(offer, ports);
    }
}
