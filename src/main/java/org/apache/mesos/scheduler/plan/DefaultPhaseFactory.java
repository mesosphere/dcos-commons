package org.apache.mesos.scheduler.plan;

import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.specification.TaskSet;
import org.apache.mesos.specification.TaskSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This class generates Phases given PhaseSpecifications.
 */
public class DefaultPhaseFactory {

    private final BlockFactory blockFactory;

    public DefaultPhaseFactory(BlockFactory blockFactory) {
       this.blockFactory = blockFactory;
    }

    public Phase getPhase(TaskSet taskSet) throws InvalidRequirementException {
        return DefaultPhase.create(
                UUID.randomUUID(),
                taskSet.getName(),
                getBlocks(taskSet));
    }

    private List<Block> getBlocks(TaskSet taskSet) throws InvalidRequirementException {
        List<Block> blocks = new ArrayList<>();

        for (TaskSpecification taskSpecification : taskSet.getTaskSpecifications()) {
            blocks.add(blockFactory.getBlock(taskSpecification));
        }

        return blocks;
    }
}
