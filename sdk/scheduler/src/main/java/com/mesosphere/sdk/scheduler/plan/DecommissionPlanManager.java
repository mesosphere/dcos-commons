package com.mesosphere.sdk.scheduler.plan;

import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * The PlanManager which governs decommissioning of pods.
 */
public class DecommissionPlanManager extends DefaultPlanManager {
    private final Collection<Protos.TaskInfo> tasksToDecommission;

    public DecommissionPlanManager(Plan plan, Collection<Protos.TaskInfo> tasksToDecommission) {
        super(plan);
        this.tasksToDecommission = tasksToDecommission;
    }

    public Collection<Protos.TaskInfo> getTasksToDecommission() {
        return tasksToDecommission;
    }
}
