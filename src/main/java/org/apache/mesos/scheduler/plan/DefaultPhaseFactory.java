package org.apache.mesos.scheduler.plan;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.specification.TaskSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This class generates Phases given PhaseSpecifications.
 */
public class DefaultPhaseFactory implements PhaseFactory {

    private final BlockFactory blockFactory;

    public DefaultPhaseFactory(BlockFactory blockFactory) {
        this.blockFactory = blockFactory;
    }

    public Phase getPhase(TaskSet taskSet) {
        return new DefaultPhase(taskSet.getName(), getBlocks(taskSet), new SerialStrategy<>(), Collections.emptyList());
    }

    private List<Element> getBlocks(TaskSet taskSet) {
        return taskSet.getTaskSpecifications().stream()
                .map(taskSpec -> {
                    try {
                        return blockFactory.getBlock(taskSpec);
                    } catch (Block.InvalidException e) {
                        return new DefaultBlock(
                                taskSpec.getName(),
                                Optional.empty(),
                                Status.ERROR,
                                Arrays.asList(ExceptionUtils.getStackTrace(e)));
                    }
                })
                .collect(Collectors.toList());
    }
}
