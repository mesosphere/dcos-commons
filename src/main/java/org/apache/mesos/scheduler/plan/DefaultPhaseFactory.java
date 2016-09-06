package org.apache.mesos.scheduler.plan;

import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.specification.PhaseSpecification;
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

    public Phase getPhase(PhaseSpecification phaseSpecification) throws InvalidRequirementException {
        return DefaultPhase.create(
                UUID.randomUUID(),
                phaseSpecification.getName(),
                getBlocks(phaseSpecification));
    }

    private List<Block> getBlocks(PhaseSpecification phaseSpecification) throws InvalidRequirementException {
        List<Block> blocks = new ArrayList<>();

        for (TaskSpecification taskSpecification : phaseSpecification.getTaskSpecifications()) {
            blocks.add(blockFactory.getBlock(taskSpecification));
        }

        return blocks;
    }
}
