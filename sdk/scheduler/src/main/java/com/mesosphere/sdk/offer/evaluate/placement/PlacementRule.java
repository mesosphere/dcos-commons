package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.Collection;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
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
     * Returns whether the provided {@link Offer}, combined with the accompanying {@link OfferRequirement} and
     * {@link TaskInfo}s, passes or fails this placement constraint.
     *
     * @param offer the offer to be examined
     * @param offerRequirement the offer requirement describing what's to be done with this offer
     * @param tasks the currently deployed tasks in the system, possibly including a duplicate
     *              of the task being launched as represented in the offerRequirement. Use
     *              {@link PlacementUtils#areEquivalent(TaskInfo, OfferRequirement)} to detect
     *              duplicates
     * @return an {@link EvaluationOutcome} object describing whether the placement succeeded or failed and why
     */
    EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks);

    /**
     * Must be explicitly implemented by all PlacementRules.
     *
     * @see com.mesosphere.sdk.offer.TaskUtils#areDifferent(
     * com.mesosphere.sdk.specification.TaskSpec,
     * com.mesosphere.sdk.specification.TaskSpec)
     */
    boolean equals(Object o);
}
