package com.mesosphere.sdk.scheduler.plan;

import org.apache.mesos.Protos;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides the default implementation of a {@link PlanManager}.
 * Encapsulates the plan and a strategy for executing that plan.
 */
public class DefaultPlanManager implements PlanManager {
    private final Plan plan;

    /**
     * Creates a new plan manager for the provided {@link Plan}, which will not be set to an interrupted state.
     */
    public static DefaultPlanManager createProceeding(Plan plan) {
        return new DefaultPlanManager(plan);
    }

    /**
     * Creates a new plan manager for the provided {@link Plan}, which will be set to an interrupted state.
     */
    public static DefaultPlanManager createInterrupted(Plan plan) {
        plan.interrupt();
        return new DefaultPlanManager(plan);
    }

    private DefaultPlanManager(final Plan plan) {
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
        return getDirtyAssets(plan);
    }

    public static Set<PodInstanceRequirement> getDirtyAssets(Plan plan) {
        if (plan == null) {
            return Collections.emptySet();
        }

        return plan.getChildren().stream()
                .flatMap(phase -> phase.getChildren().stream())
                .filter(step -> step.isAssetDirty() && step.getPodInstanceRequirement().isPresent())
                .map(step -> step.getPodInstanceRequirement().get())
                .collect(Collectors.toSet());
    }
}
