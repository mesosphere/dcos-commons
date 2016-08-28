package org.apache.mesos.scheduler.plan;

import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.BlockFactory;
import org.apache.mesos.scheduler.plan.DefaultPhase;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.specification.PhaseSpecification;
import org.apache.mesos.specification.TaskSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by gabriel on 8/28/16.
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
