package org.apache.mesos.scheduler.plan;

import java.util.UUID;

/**
 * A Phase PhaseStrategy governs when Blocks should be returned for procesing.
 */
public interface PhaseStrategy {

    /**
     *
     * @return The current Block selected by the strategy for execution.
     */
    Block getCurrentBlock();

    /**
     * If the current Stage is interrupted at this Phase. This phase should
     * now proceed with execution.
     */
    void proceed();

    /**
     * If the current State is proceeding at this Phase. This phase should
     * not be interrupted.
     */
    void interrupt();

    /**
     * Restart phase execution at the current block.
     * @param blockId The unique id of the block to be restarted.
     */
    void restart(UUID blockId);

    /**
     * Force the block to a status of Complete.
     * @param blockId The unique id of the block to be completed.
     */
    void forceComplete(UUID blockId);

    /**
     * Get the status of the phase.
     * @return
     */
    Status getStatus();

    /**
     *
     * @return The phase associated with this strategy.
     */
    Phase getPhase();

    /**
     * @return True if the Phase is interrupted.
     */
    boolean isInterrupted();

    /**
     *
     * @param block The Block to test for a descision point.
     * @return True if this phase contains the block and a decision point is
     * enabled for the Block.
     */
    boolean hasDecisionPoint(Block block);
}
