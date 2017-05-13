package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;

import java.util.*;

/**
 * The Resource Cleaner provides recommended operations for cleaning up
 * unexpected Reserved resources and persistent volumes.
 */
public interface ResourceCleaner {

    /**
     * Returns a list of operations which should be performed, given the provided list of Offers
     * from Mesos. The returned operations MUST be performed in the order in which they are
     * provided.
     */
    default List<OfferRecommendation> evaluate(List<Offer> offers) {
        // ORDERING IS IMPORTANT:
        //    The resource lifecycle is RESERVE -> CREATE -> DESTROY -> UNRESERVE
        //    Therefore we *must* put any DESTROY calls before any UNRESERVE calls
        List<OfferRecommendation> recommendations = new ArrayList<>();

        // First, find any persistent volumes to be DESTROYed
        for (Offer offer : offers) {
            for (Resource persistentVolume : getPersistentVolumesToBeDestroyed(offer)) {
                recommendations.add(new DestroyOfferRecommendation(offer, persistentVolume));
            }
        }

        // Then, find any unexpected persistent volumes AND resource reservations which should
        // (both) be UNRESERVEd
        for (Offer offer : offers) {
            for (Resource reservedResource : getReservedResourcesToBeUnreserved(offer)) {
                recommendations.add(new UnreserveOfferRecommendation(offer, reservedResource));
            }
        }

        return recommendations;
    }

    /**
     * Examines the {@link Offer} to determine which reserved {@link Resource}s should be unreserved.
     *
     * @param offer The {@link Offer} containing the {@link Resource}s.
     * @return A {@link Collection} of {@link Resource}s that should be unreserved.
     */
    Collection<? extends Resource> getReservedResourcesToBeUnreserved(Offer offer);

    /**
     * Examines the {@link Offer} to determine which volume {@link Resource}s should be destroyed.
     *
     * @param offer The {@link Offer} containing the persistent volume {@link Resource}s.
     * @return A {@link Collection} of {@link Resource}s that should be destroyed.
     */
    Collection<? extends Resource> getPersistentVolumesToBeDestroyed(Offer offer);
}
