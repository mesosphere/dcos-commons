package org.apache.mesos.scheduler.plan;

import java.util.Objects;
import java.util.UUID;

/**
 * Strategy for Reconciliation.
 */
public class ReconciliationStrategy implements PhaseStrategy {

    public static ReconciliationStrategy create(final ReconciliationPhase phase) {
        return new ReconciliationStrategy(phase);
    }

    private final ReconciliationPhase phase;

    private ReconciliationStrategy(final ReconciliationPhase phase) {
        this.phase = phase;
    }

    @Override
    public Block getCurrentBlock() {
        // ReconciliationPhases only have a single Block
        return phase.getBlock(0);
    }

    @Override
    public void proceed() {
        // no-op: interrupting reconciliation not supported
    }

    @Override
    public void interrupt() {
        // no-op: interrupting reconciliation not supported
    }

    @Override
    public void restart(UUID blockId) {
        getCurrentBlock().restart();
    }

    @Override
    public void forceComplete(UUID blockId) {
        getCurrentBlock().forceComplete();
    }

    @Override
    public Status getStatus() {
        return Block.getStatus(getCurrentBlock());
    }

    @Override
    public boolean isInterrupted() {
        return false;
    }

    @Override
    public Phase getPhase() {
        return phase;
    }

    @Override
    public boolean hasDecisionPoint(Block block) {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReconciliationStrategy)) {
            return false;
        }
        ReconciliationStrategy that = (ReconciliationStrategy) o;
        return Objects.equals(getPhase(), that.getPhase());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPhase());
    }

    @Override
    public String toString() {
        return "ReconciliationStrategy{phase=" + phase + '}';
    }
}
