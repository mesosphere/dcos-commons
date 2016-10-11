package org.apache.mesos.scheduler.plan;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@code RandomRecoveryStrategy} extends {@link DefaultInstallStrategy}, by providing a random block selection
 * strategy.
 */
public class RandomRecoveryStrategy extends DefaultInstallStrategy {
    public RandomRecoveryStrategy(Phase phase) {
        super(phase);
    }

    @Override
    public Optional<Block> getCurrentBlock() {
        final List<? extends Block> blocks = filterOutCompletedBlocks(getPhase().getBlocks());
        if (isInterrupted() || CollectionUtils.isEmpty(blocks)) {
            return Optional.empty();
        } else {
            return Optional.of(blocks.get(new Random().nextInt(blocks.size())));
        }
    }

    /**
     * Filters blocks that are already COMPLETE.
     */
    @VisibleForTesting
    protected static List<Block> filterOutCompletedBlocks(List<? extends Block> blocks) {
        if (blocks == null) {
            return Arrays.asList();
        }
        return blocks.stream().filter(block -> !block.isComplete()).collect(Collectors.toList());
    }
}
