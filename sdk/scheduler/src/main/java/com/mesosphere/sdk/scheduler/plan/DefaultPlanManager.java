package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.ChainedObserver;
import org.apache.mesos.Protos;

import java.util.*;

/**
 * Provides the default implementation of a {@link PlanManager}.
 * Encapsulates the plan and a strategy for executing that plan.
 */
public class DefaultPlanManager extends ChainedObserver implements PlanManager {
    private final Plan plan;

    public DefaultPlanManager(final Plan plan) {
        // All plans begin in an interrupted state.  The deploy plan will
        // be automatically proceeded when appropriate.  Other plans are
        // sidecar plans and should be externally proceeded.
        plan.interrupt();
        this.plan = plan;
        this.plan.subscribe(this);
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
        Set<PodInstanceRequirement> dirtyAssets = new HashSet<>();
        final List<? extends Phase> phases = plan.getChildren();
        for (Phase phase : phases) {
            final List<? extends Step> steps = phase.getChildren();
            for (Step step : steps) {
                if (step.isAssetDirty()) {
                    Optional<PodInstanceRequirement> dirtyAsset = step.getAsset();
                    if (dirtyAsset.isPresent()) {
                        dirtyAssets.add(dirtyAsset.get());
                    }
                }
            }
        }
        return dirtyAssets;
    }
}
