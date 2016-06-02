package org.apache.mesos.scheduler.plan;

import java.util.List;
import java.util.UUID;

/**
 * Defines the interface for a phase of a plan.
 * Phases contain blocks.
 */
public interface Phase extends Completable {

    /**
     * Returns the list of Blocks in this Phase.
     *
     * @return
     */
    List<? extends Block> getBlocks();

    /**
     *
     * @param id The unique id of the Block to retrieve.
     * @return The block associated with id.
     */
    Block getBlock(UUID id);

    /**
     * @param index The index of the Block to retrieve.
     * @return The block at index.
     */
    Block getBlock(int index);

    /**
     * Returns a unique identifier for the Phase.
     *
     * @return
     */
    UUID getId();

    /**
     * Returns a human readable name for the Phase.
     *
     * @return
     */
    String getName();
}
