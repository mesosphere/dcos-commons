package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.ChainedObserver;

import java.util.Collection;

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
}
