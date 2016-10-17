package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.offer.OfferRequirement;

import java.util.Collection;
import java.util.Optional;

/**
 * Defines the interface for a Block of a {@link Phase}. The block is the base unit of a set of
 * tasks to perform, such as launching a Task, updating a Task, or reconciling Mesos state with
 * Framework state. A Block may be in one of four states: PENDING, IN_PROGRESS, COMPLETE, or ERROR.
 * <p>
 * See {@Stage} docs for more background.
 */
public interface Block extends Element {
    /**
     * Starts the Block, whose {@link Status} should be {@link Status#PENDING}. Returns an
     * {@link OfferRequirement}, or an empty Optional if obtaining/updating resource requirements are not
     * applicable to the Block. This will continue to be called for as long as {@link Element#isPending()} returns
     * true.
     *
     * @see {@link #updateOfferStatus(Collection<Offer.Operation>)} which returns the outcome of the
     *      {@link OfferRequirement}
     */
    Optional<OfferRequirement> start();

    /**
     * Notifies the Block whether the {@link OfferRequirement} previously returned by
     * {@link #start()} has been successfully accepted/fulfilled. The {@code operations} param is
     * empty when no offers matching the requirement previously returned by {@link #clone()
     * could be found. This is only called if {@link #start()} returned a non-{@code null}
     * {@link OfferRequirement}.
     */
    void updateOfferStatus(Collection<Offer.Operation> operations);

    /**
     * Thrown on invalid Block construction attempt.
     */
    class InvalidException extends Exception {
        public InvalidException(Exception e) {
            super(e);
        }
    }
}
