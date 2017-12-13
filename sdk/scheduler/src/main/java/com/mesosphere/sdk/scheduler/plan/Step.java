package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.OfferRecommendation;

import java.util.Collection;
import java.util.Optional;

/**
 * Defines the interface for a Step of a {@link Phase}. The {@link Step} is the base unit of a set of
 * tasks to perform, such as launching a Task, updating a Task, or reconciling Mesos state with
 * Framework state. A Step may be in one of five states: PENDING, PREPARED, STARTING, COMPLETE, or ERROR.
 *
 * See {@link Plan} docs for more background.
 */
public interface Step extends Element, Interruptible {
    /**
     * Starts the Step, whose {@link Status} should be {@link Status#PENDING}. Returns an
     * {@link PodInstanceRequirement}, or an empty Optional if obtaining/updating resource requirements are not
     * applicable to the Step. This will continue to be called for as long as {@link Element#isPending()} returns
     * true.
     *
     * @see {@link #updateOfferStatus(Collection<org.apache.mesos.Protos.Offer.Operation>)} which returns the outcome of
     *      the {@link PodInstanceRequirement}
     */
    Optional<PodInstanceRequirement> start();

    /**
     * Return the pod instance that this Step intends to work on.
     *
     * @return The information about the pod that this Step intends to work on, Optional.empty() otherwise.
     */
    Optional<PodInstanceRequirement> getPodInstanceRequirement();

    /**
     * Notifies the Step whether the {@link PodInstanceRequirement} previously returned by
     * {@link #start()} has been successfully accepted/fulfilled. The {@code recommendations} param is
     * empty when no offers matching the requirement previously returned by {@link #start()}
     * could be found. This is only called if {@link #start()} returned a non-empty
     * {@link PodInstanceRequirement}.
     */
    void updateOfferStatus(Collection<OfferRecommendation> recommendations);

    /**
     * Returns a user-facing display status of this step, which may provide additional context on the work being
     * performed beyond the underlying progress {@link Status} returned by {@link Element#getStatus()}.
     */
    default String getDisplayStatus() {
        return getStatus().toString();
    }

    /**
     * Reports whether the Asset associated with this Step is dirty.
     */
    default boolean isAssetDirty() {
        return isPrepared() || isStarting();
    }

    @Override
    default boolean isEligible(Collection<PodInstanceRequirement> dirtyAssets) {
        return Element.super.isEligible(dirtyAssets) &&
                !isInterrupted() &&
                !(getPodInstanceRequirement().isPresent()
                        && PlanUtils.assetConflicts(getPodInstanceRequirement().get(), dirtyAssets));
    }

    @Override
    default String getMessage() {
        if (!getStatus().toString().equals(getDisplayStatus())) {
            // Include the custom display status:
            return Element.super.getMessage() + String.format(" (display:%s)", getDisplayStatus());
        } else {
            return Element.super.getMessage();
        }
    }
}
