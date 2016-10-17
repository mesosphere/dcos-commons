package org.apache.mesos.scheduler.plan;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Provides the default implementation of a {@link PlanManager}.
 * Encapsulates the plan and a strategy for executing that plan.
 */
public class DefaultPlanManager implements PlanManager {
    private final Plan plan;

    public DefaultPlanManager(final Plan plan) {
        this.plan = plan;
    }

    @Override
    public Plan getPlan() {
        return plan;
    }

    @Override
    public Collection<? extends Block> getCandidates(Collection<String> dirtyAssets) {
        Collection<Phase> candidatePhases = plan.getStrategy().getCandidates(plan, dirtyAssets);
        Collection<Block> candidateBlocks = candidatePhases.stream()
                .map(phase -> phase.getStrategy().getCandidates(phase, dirtyAssets))
                .flatMap(blocks -> blocks.stream())
                .collect(Collectors.toList());

        return candidateBlocks;
    }
}
