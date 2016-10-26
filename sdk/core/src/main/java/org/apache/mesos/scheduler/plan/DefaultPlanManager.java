package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.ChainedObserver;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides the default implementation of a {@link PlanManager}.
 * Encapsulates the plan and a strategy for executing that plan.
 */
public class DefaultPlanManager extends ChainedObserver implements PlanManager {
    private final Plan plan;

    public DefaultPlanManager(final Plan plan) {
        this.plan = plan;
        this.plan.subscribe(this);
    }

    @Override
    public Plan getPlan() {
        return plan;
    }

    @Override
    public Collection<? extends Block> getCandidates(Collection<String> dirtyAssets) {
        return PlanUtils.getCandidates(plan, dirtyAssets);
    }

    @Override
    public void update(Protos.TaskStatus status) {
        plan.update(status);
    }

    @Override
    public Set<String> getDirtyAssets() {
        Set<String> dirtyAssets = new HashSet<>();
        final List<? extends Phase> phases = plan.getChildren();
        for (Phase phase : phases) {
            final List<? extends Block> blocks = phase.getChildren();
            for (Block block : blocks) {
                if (block.isInProgress()) {
                    dirtyAssets.add(block.getName());
                }
            }
        }
        return dirtyAssets;
    }
}
