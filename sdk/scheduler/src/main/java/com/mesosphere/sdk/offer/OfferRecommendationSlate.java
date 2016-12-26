package com.mesosphere.sdk.offer;

import java.util.ArrayList;
import java.util.List;

/**
 * This class maintains a running representation of all possible offer recommendations to be made against a single
 * offer: {@link UnreserveOfferRecommendation}, {@link ReserveOfferRecommendation}, {@link CreateOfferRecommendation},
 * and {@link LaunchOfferRecommendation}.
 */
public class OfferRecommendationSlate {
    private List<OfferRecommendation> unreserves;
    private List<OfferRecommendation> reserves;
    private List<OfferRecommendation> creates;
    private List<OfferRecommendation> launches;

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
    public void addUnreserveRecommendation(OfferRecommendation recommendation) {
        unreserves.add(recommendation);
    }

    /**
     * Add an {@link ReserveOfferRecommendation} to this slate.
     * @param recommendation
     */
    public void addReserveRecommendation(OfferRecommendation recommendation) {
        reserves.add(recommendation);
    }

    /**
     * Add an {@link CreateOfferRecommendation} to this slate.
     * @param recommendation
     */
    public void addCreateRecommendation(OfferRecommendation recommendation) {
        creates.add(recommendation);
    }

    /**
     * Add an {@link LaunchOfferRecommendation} to this slate.
     * @param recommendation
     */
    public void addLaunchRecommendation(OfferRecommendation recommendation) {
        launches.add(recommendation);
    }

    /**
     * Get all recommendations made for this offer.
     * @return a {@link List} of {@link OfferRecommendation}
     */
    public List<OfferRecommendation> getRecommendations() {
        List<OfferRecommendation> recommendations = new ArrayList<>();
        recommendations.addAll(unreserves);
        recommendations.addAll(reserves);
        recommendations.addAll(creates);
        recommendations.addAll(launches);

        return recommendations;
    }
}
