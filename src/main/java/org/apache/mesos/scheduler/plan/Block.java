package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;

import java.util.UUID;

/**
 * Defines the interface for a block of a plan.
 */
public interface Block extends Completable {

    /**
     * Returns the Status of the Block.
     *
     * @return
     */
    Status getStatus();

    /**
     * Set the Status of the Block.
     *
     * @return
     */
    void setStatus(Status newStatus);

    /**
     * Indicates if the Block has not been started.
     *
     * @return
     */
    boolean isPending();

    /**
     * Indicates if the Block is running but not complete.
     *
     * @return
     */
    boolean isInProgress();

    /**
     * Starts a Block.
     * Returns the Block's OfferRequirement or null, if it is not yet ready to be launched.
     * It may require multiple calls before a Block is ready to be launched can provide an
     * OfferRequirement.
     *
     * @return
     */
    OfferRequirement start();

    /**
     * Provides ability for the Stage Manager to provide TaskStatus updates to the Block.
     *
     * @param status
     */
    void update(Protos.TaskStatus status);

    /**
     * @return The unique identifier of the block.
     */
    UUID getId();

    /**
     * @return Any message that the block wishes to communicate to the system.
     */
    String getMessage();

    /**
     * @return A human readable name for the block.
     */
    String getName();
}
