package org.apache.mesos.scheduler.plan;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.Collection;
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
    public Optional<Block> getCurrentBlock(Collection<String> dirtiedAssets) {
        // ReconciliationPhases only have a single Block
        for (Block block : phase.getBlocks()) {
            if (!dirtiedAssets.contains(block.getName())) {
                return Optional.of(block);
            }
        }
        return Optional.empty();
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
        for (Block block: phase.getBlocks()) {
            if (block.getId().equals(blockId)) {
                block.restart();
            }
        }
    }

    @Override
    public void forceComplete(UUID blockId) {
        for (Block block: phase.getBlocks()) {
            if (block.getId().equals(blockId)) {
                block.forceComplete();
            }
        }
    }

    @Override
    public Status getStatus() {
        for (Block block : phase.getBlocks()) {
            if (!block.isComplete()) {
                return Block.getStatus(block);
            }
        }

        return Status.COMPLETE;
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
