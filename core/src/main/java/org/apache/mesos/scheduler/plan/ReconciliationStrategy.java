package org.apache.mesos.scheduler.plan;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.Objects;
import java.util.Optional;
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
    public Optional<Block> getCurrentBlock() {
        // ReconciliationPhases only have a single Block
        return Optional.of(phase.getBlock(0));
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
        getCurrentBlock().get().restart();
    }

    @Override
    public void forceComplete(UUID blockId) {
        getCurrentBlock().get().forceComplete();
    }

    @Override
    public Status getStatus() {
        return Block.getStatus(getCurrentBlock().get());
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
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPhase());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
