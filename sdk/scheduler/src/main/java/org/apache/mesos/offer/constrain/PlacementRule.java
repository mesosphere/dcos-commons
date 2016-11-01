package org.apache.mesos.offer.constrain;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.offer.OfferRequirement;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Implements placement constraint logic on an offer, filtering out the Resources which are not
 * to be used according to the filter logic.
 *
 * Accepts an Offer and returns a copy of that Offer with any disallowed Resources filtered out, or
 * may return the original Offer without copying if everything passes the filter. If the entire
 * Offer is rejected, the return value will be a non-{@code null} Offer with zero Resources. A
 * PlacementRule may contain multiple internal checks, and may even be composed of other sub-Rules.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public interface PlacementRule {

    /**
     * Returns a copy of that Offer with any disallowed Resources filtered out, or may return the
     * original Offer without copying if everything passes the filter. If the entire Offer is
     * rejected, the return value will be a non-{@code null} Offer with zero Resources.
     *
     * @param offer the offer to be examined
     * @param offerRequirement the offer requirement describing what's to be done with this offer
     * @return either the uncopied input as-is, or a copy of the input with zero or more Resources
     *     stripped out
     */
    public Offer filter(Offer offer, OfferRequirement offerRequirement);

    /**
     * Must be explicitly implemented by all PlacementRules.
     */
    public boolean equals(Object o);
}
