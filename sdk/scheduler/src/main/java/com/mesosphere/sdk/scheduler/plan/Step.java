package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.scheduler.Observable;

import java.util.Collection;
import java.util.Optional;

/**
 * Defines the interface for a Step of a {@link Phase}. The {@link Step} is the base unit of a set of
 * tasks to perform, such as launching a Task, updating a Task, or reconciling Mesos state with
 * Framework state. A Step may be in one of five states: PENDING, PREPARED, STARTING, COMPLETE, or ERROR.
 *
 * A {@link Step} is an {@link Observable}, and will notify its observers when its state changes.
 * <p>
 * See {@link Plan} docs for more background.
 */
public interface Step extends Element, Interruptible {
    /**
     * Starts the Step, whose {@link Status} should be {@link Status#PENDING}. Returns an
     * {@link OfferRequirement}, or an empty Optional if obtaining/updating resource requirements are not
     * applicable to the Step. This will continue to be called for as long as {@link Element#isPending()} returns
     * true.
     *
     * @see {@link #updateOfferStatus(Collection<org.apache.mesos.Protos.Offer.Operation>)} which returns the outcome of
     *      the {@link OfferRequirement}
     */
    Optional<PodInstanceRequirement> start();

    /**
     * Notifies the Step whether the {@link PodInstanceRequirement} previously returned by
     * {@link #start()} has been successfully accepted/fulfilled. The {@code recommendations} param is
     * empty when no offers matching the requirement previously returned by {@link #start()}
     * could be found. This is only called if {@link #start()} returned a non-empty
     * {@link PodInstanceRequirement}.
     */
    void updateOfferStatus(Collection<OfferRecommendation> recommendations);

    /**
     * Return the Asset that this Step intends to work on.
     * @return The name of the Asset this Step intends to work on if one exists, Optional.empty() otherwise.
     */
    Optional<String> getAsset();

    /**
     * Reports whether the Asset associated with this Step is dirty.
     */
    default boolean isAssetDirty() {
        return isPrepared() || isStarting();
    }

    /**
     * Thrown on invalid Step construction attempt.
     */
    class InvalidStepException extends Exception {
        public InvalidStepException(Exception e) {
            super(e);
        }

        public InvalidStepException(String s) {
            super(s);
        }
    }
}
