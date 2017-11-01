package com.mesosphere.sdk.scheduler.plan;

import org.apache.mesos.Protos;

import java.util.*;

/**
 * Provides the default implementation of a {@link PlanManager}.
 * Encapsulates the plan and a strategy for executing that plan.
 */
public class DefaultPlanManager implements PlanManager {
    private final Plan plan;

    public DefaultPlanManager(final Plan plan) {
        // All plans begin in an interrupted state.  The deploy plan will
        // be automatically proceeded when appropriate.  Other plans are
        // sidecar plans and should be externally proceeded.
        plan.interrupt();
        this.plan = plan;
    }

    @Override
    public Plan getPlan() {
        return plan;
    }

    @Override
    public Collection<? extends Step> getCandidates(Collection<PodInstanceRequirement> dirtyAssets) {
        return plan.getCandidates(dirtyAssets);
    }

    @Override
    public void update(Protos.TaskStatus status) {
        plan.update(status);
    }

    @Override
    public Set<PodInstanceRequirement> getDirtyAssets() {
        return PlanUtils.getDirtyAssets(plan);
    }
}
