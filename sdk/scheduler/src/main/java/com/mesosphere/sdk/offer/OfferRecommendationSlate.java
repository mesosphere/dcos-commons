package com.mesosphere.sdk.offer;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer.Operation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class maintains a running representation of all possible offer recommendations to be made against a single
 * offer: {@link UnreserveOfferRecommendation}, {@link ReserveOfferRecommendation}, {@link CreateOfferRecommendation},
 * and {@link LaunchOfferRecommendation}.
 */
public class OfferRecommendationSlate {
    private List<UnreserveOfferRecommendation> unreserves;
    private List<ReserveOfferRecommendation> reserves;
    private List<CreateOfferRecommendation> creates;
    private List<LaunchOfferRecommendation> launches;

    public OfferRecommendationSlate() {
        this.unreserves = new ArrayList<>();
        this.reserves = new ArrayList<>();
        this.creates = new ArrayList<>();
        this.launches = new ArrayList<>();
    }

    /**
     * Add an {@link UnreserveOfferRecommendation} to this slate.
     * @param recommendation
     */
    public void addUnreserveRecommendation(UnreserveOfferRecommendation recommendation) {
        unreserves.add(recommendation);
    }

    /**
     * Add an {@link ReserveOfferRecommendation} to this slate.
     * @param recommendation
     */
    public void addReserveRecommendation(ReserveOfferRecommendation recommendation) {
        reserves.add(recommendation);
    }

    /**
     * Add an {@link CreateOfferRecommendation} to this slate.
     * @param recommendation
     */
    public void addCreateRecommendation(CreateOfferRecommendation recommendation) {
        creates.add(recommendation);
    }

    /**
     * Add an {@link LaunchOfferRecommendation} to this slate.
     * @param recommendation
     */
    public void addLaunchRecommendation(LaunchOfferRecommendation recommendation) {
        launches.add(recommendation);
    }

    /**
     * Get all recommendations made for this offer.
     * @return a {@link List} of {@link OfferRecommendation}
     */
    public List<OfferRecommendation> getRecommendations() {
        List<OfferRecommendation> recommendations = new ArrayList<>();
        recommendations.addAll(coalescePortRecommendations(unreserves));
        recommendations.addAll(coalescePortRecommendations(reserves));
        recommendations.addAll(creates);
        recommendations.addAll(launches);

        return recommendations;
    }

    private <R extends OfferRecommendation> List<OfferRecommendation> coalescePortRecommendations(
            List<R> offerRecommendations) {
        if (offerRecommendations.isEmpty()) {
            return Collections.emptyList();
        }

        Protos.Resource ports = null;
        List<OfferRecommendation> recommendations = new ArrayList<>();
        for (OfferRecommendation recommendation : offerRecommendations) {
            Protos.Resource resource = getOperationResource(recommendation.getOperation());
            if (ports == null && resource.getName().equals(Constants.PORTS_RESOURCE_TYPE)) {
                ports = resource;
            } else if (resource.getName().equals(Constants.PORTS_RESOURCE_TYPE)) {
                ports = ResourceUtils.mergeRanges(ports, resource);
            } else {
                recommendations.add(recommendation);
            }
        }

        OfferRecommendation first = offerRecommendations.get(0);
        if (ports != null) {
            recommendations.add(first.getOperation().getType().equals(Operation.Type.RESERVE) ?
                    new ReserveOfferRecommendation(first.getOffer(), ports) :
                    new UnreserveOfferRecommendation(first.getOffer(), ports));
        }

        return recommendations;
    }

    private Protos.Resource getOperationResource(Operation operation) {
        if (operation.hasReserve()) {
            return operation.getReserve().getResources(0);
        } else if (operation.hasUnreserve()) {
            return operation.getUnreserve().getResources(0);
        } else {
            throw new IllegalArgumentException(
                    "Operation is not of type RESERVE or UNRESERVE: " + TextFormat.shortDebugString(operation));
        }
    }
}
