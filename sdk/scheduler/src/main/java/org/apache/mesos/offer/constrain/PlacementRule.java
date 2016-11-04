package org.apache.mesos.offer.constrain;

import java.util.Collection;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.OfferRequirement;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Implements placement constraint logic on an offer, filtering out the Resources which are not
 * to be used according to the filter logic.
 *
 * Accepts an Offer and returns a copy of that Offer with any disallowed Resources filtered out, or
 * may return the original Offer without copying if everything passes the filter. If the entire
 * Offer is rejected, the return value will be a non-{@code null} Offer with zero Resources.
 *
 * PlacementRules may contain multiple internal checks, and may even be a composition of other
 * PlacementRules.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface PlacementRule {

    /**
     * Returns a copy of that Offer with any disallowed Resources filtered out, or may return the
     * original Offer without copying if everything passes the filter. If the entire Offer is
     * rejected, the return value will be a non-{@code null} Offer with zero Resources.
     *
     * @param offer the offer to be examined
     * @param offerRequirement the offer requirement describing what's to be done with this offer
     * @param tasks the currently deployed tasks in the system, possibly including a duplicate
     *              of the task being launched as represented in the offerRequirement. Use
     *              {@link PlacementUtils#areEquivalent(TaskInfo, OfferRequirement)} to detect
     *              duplicates
     * @return either the uncopied input as-is, or a copy of the input with zero or more Resources
     *     stripped out
     */
    Offer filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks);

    /**
     * Must be explicitly implemented by all PlacementRules.
     *
     * @see org.apache.mesos.offer.TaskUtils#areDifferent(
     * org.apache.mesos.specification.TaskSpecification,
     * org.apache.mesos.specification.TaskSpecification)
     */
    boolean equals(Object o);
}
