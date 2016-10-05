package org.apache.mesos.scheduler.plan;

import org.apache.commons.collections.CollectionUtils;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * RandomRecoveryStrategy.
 */
public class RandomRecoveryStrategy extends DefaultInstallStrategy {
    public RandomRecoveryStrategy(Phase phase) {
        super(phase);
    }

    @Override
    public Optional<Block> getCurrentBlock() {
        final List<? extends Block> blocks = getPhase().getBlocks();
        if (isInterrupted() || CollectionUtils.isEmpty(blocks)) {
            return Optional.empty();
        } else {
            return Optional.of(blocks.get(new Random().nextInt(blocks.size())));
        }
    }
}
