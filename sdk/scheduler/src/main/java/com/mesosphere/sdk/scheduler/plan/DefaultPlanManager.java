package com.mesosphere.sdk.scheduler.plan;

import org.apache.mesos.Protos;
import com.mesosphere.sdk.scheduler.ChainedObserver;

import java.util.Collection;
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
    public Collection<? extends Step> getCandidates(Collection<String> dirtyAssets) {
        return PlanUtils.getCandidates(plan, dirtyAssets);
    }

    @Override
    public void update(Protos.TaskStatus status) {
        plan.update(status);
    }

    @Override
    public Set<String> getDirtyAssets() {
        return PlanUtils.getDirtyAssets(plan);
    }
}
