package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;

import java.util.UUID;

/**
 * Defines the interface for a Block of a {@link Phase}. The block is the base unit of a set of
 * tasks to perform, such as launching a Task, updating a Task, or reconciling Mesos state with
 * Framework state. A Block may be in one of four states: PENDING, IN_PROGRESS, COMPLETE, or ERROR.
 * <p>
 * See {@Stage} docs for more background.
 */
public interface Block extends Completable {

    /**
     * Utility method which maps the provided {@link Block} to its corresponding {@link Status}.
     */
    static Status getStatus(Block block) {
        // these should all be mutually exclusive, ordering doesn't matter.
        if (block.isPending()) {
            return Status.PENDING;
        } else if (block.isInProgress()) {
            return Status.IN_PROGRESS;
        } else if (block.isComplete()) {
            return Status.COMPLETE;
        }
        return Status.ERROR;
    }

    /**
     * Returns whether the {@link Status} of the Block is {@link Status.Pending}, ie whether the
     * Scheduler should call {@link start()} for this Block. This must be {@code false} if either
     * {@link #isComplete()} or {@link #isInProgress()} is {@code true}.
     */
    boolean isPending();

    /**
     * Returns whether the {@link Status} of the Block is {@link Status.InProgress}. This must be
     * {@code false} if either {@link #isComplete()} or {@link #isPending()} is {@code true}.
     */
    boolean isInProgress();

    /**
     * Starts the Block, whose {@link Status} should be {@link Status.Pending}. Returns an
     * {@link OfferRequirement}, or {@code null} if obtaining/updating resource requirements are not
     * applicable to the Block. This will continue to be called for as long as {@link isPending()}
     * returns {@code true}.
     *
     * @see {@link #isPending()} which is the gate to whether {@code start()} is called
     * @see {@link #updateOfferStatus(boolean)} which returns the outcome of the
     *      {@link OfferRequirement}
     */
    OfferRequirement start();

    /**
     * Notifies the Block whether the {@link OfferRequirement} previously returned by
     * {@link #start()} has been successfully accepted/fulfilled. The {@code accepted} param is
     * {@code false} when no offers matching the requirement previously returned by {@link #clone()
     * could be found. This is only called if {@link #start()} returned a non-{@code null}
     * {@link OfferRequirement}.
     */
    void updateOfferStatus(boolean accepted);

    /**
     * Forcefully restarts the block, putting it into a PENDING state, waiting to be resumed with
     * a call to {@link start()}.
     */
    void restart();

    /**
     * Forcefully marks the block as COMPLETE, cancelling any work that hasn't started or that's
     * currently in progress.
     */
    void forceComplete();

    /**
     * Provides the Block with a recent {@link Protos.TaskStatus} update which was received from
     * Mesos.
     */
    void update(Protos.TaskStatus status);

    /**
     * Returns the unique identifier of the block.
     */
    UUID getId();

    /**
     * Returns a user-visible message describing the current status of the Block.
     */
    String getMessage();

    /**
     * Returns a user-visible name describing the purpose of the Block.
     */
    String getName();
}
