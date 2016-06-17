package org.apache.mesos.scheduler.plan;

import java.util.List;
import java.util.UUID;

/**
 * Defines the interface for one {@link Phase} within a {@link Stage}. A Phase is an ordered list of
 * one or more {@link Blocks}s, which each describe a single unit of work.
 * <p>
 * For example, a Block might represent a cluster task that needs to be updated, while the Phase is
 * a list of all of those cluster tasks.
 * <p>
 * See {@Stage} docs for more background.
 */
public interface Phase extends Completable {

    /**
     * Returns the list of all {@link Block}s in this {@link Phase}.
     *
     * @see #getBlock(UUID)
     * @see #getBlock(int)
     */
    List<? extends Block> getBlocks();

    /**
     * Returns the {@link Block} which has the provided {@code id}, or {@code null} if no blocks
     * match. This is equivalent to scanning the list returned by {@link getBlocks()} for a matching
     * value against {@link Block#getId()}.
     *
     * @see #getBlocks()
     * @see #getBlock(int)
     */
    Block getBlock(UUID id);

    /**
     * Returns the {@link Block} at the provided {@code index}, or {@code null} if the index exceeds
     * the size of the list. This is equivalent to calling {@code getBlocks().get(index)}, with the
     * addition of a size check.
     *
     * @see #getBlocks()
     * @see #getBlock(UUID)
     */
    Block getBlock(int index);

    /**
     * Returns the globally unique identifier for this Phase.
     */
    UUID getId();

    /**
     * Returns a user-visible descriptive name for this Phase.
     */
    String getName();
}
