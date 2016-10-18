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
    public Optional<Block> getCurrentBlock(Collection<String> dirtiedAssets) {
        final List<? extends Block> blocks = filterOnlyPendingBlocks(getPhase().getBlocks());
        if (isInterrupted() || CollectionUtils.isEmpty(blocks) || blocks.size() <= dirtiedAssets.size()) {
            return Optional.empty();
        } else {
            List<Block> cleanBlocks = blocks.stream()
                    .filter(block -> !dirtiedAssets.contains(block.getName()))
                    .collect(Collectors.toList());

            return Optional.of(cleanBlocks.get(new Random().nextInt(cleanBlocks.size())));
        }
    }

    /**
     * Filters blocks that are PENDING.
     */
    @VisibleForTesting
    protected static List<Block> filterOnlyPendingBlocks(List<? extends Block> blocks) {
        if (blocks == null) {
            return Arrays.asList();
        }
        return blocks.stream().filter(block -> block.isPending()).collect(Collectors.toList());
    }
}
