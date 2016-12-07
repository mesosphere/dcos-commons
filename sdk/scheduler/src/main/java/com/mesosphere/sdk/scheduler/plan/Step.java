package com.mesosphere.sdk.scheduler.plan;

import org.apache.mesos.Protos.Offer;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.scheduler.Observable;

import java.util.Collection;
import java.util.Optional;

/**
 * Defines the interface for a Step of a {@link Phase}. The {@link Step} is the base unit of a set of
 * tasks to perform, such as launching a Task, updating a Task, or reconciling Mesos state with
 * Framework state. A Step may be in one of four states: PENDING, IN_PROGRESS, COMPLETE, or ERROR.
 *
 * A {@link Step} is an {@link Observable}, and will notify its observers when its state changes.
 * <p>
 * See {@link Plan} docs for more background.
 */
@SuppressWarnings("rawtypes")
public interface Step extends Element {
    /**
     * Starts the Step, whose {@link Status} should be {@link Status#PENDING}. Returns an
     * {@link OfferRequirement}, or an empty Optional if obtaining/updating resource requirements are not
     * applicable to the Step. This will continue to be called for as long as {@link Element#isPending()} returns
     * true.
     *
     * @see {@link #updateOfferStatus(Collection<Offer.Operation>)} which returns the outcome of the
     *      {@link OfferRequirement}
     */
    Optional<OfferRequirement> start();

    /**
     * Notifies the Step whether the {@link OfferRequirement} previously returned by
     * {@link #start()} has been successfully accepted/fulfilled. The {@code operations} param is
     * empty when no offers matching the requirement previously returned by {@link #clone()
     * could be found. This is only called if {@link #start()} returned a non-{@code null}
     * {@link OfferRequirement}.
     */
    void updateOfferStatus(Collection<Offer.Operation> operations);

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
