package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.uninstall.ResourceCleanupStep;

import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * The PlanManager which governs decommissioning of pods.
 */
public class DecommissionPlanManager extends DefaultPlanManager {
  private final Collection<Protos.TaskInfo> tasksToDecommission;

  private final Collection<ResourceCleanupStep> resourceSteps;

  public DecommissionPlanManager(
      Plan plan,
      Collection<ResourceCleanupStep> resourceSteps,
      Collection<Protos.TaskInfo> tasksToDecommission)
  {
    super(plan);
    this.resourceSteps = resourceSteps;
    this.tasksToDecommission = tasksToDecommission;
  }

  public Collection<ResourceCleanupStep> getResourceSteps() {
    return resourceSteps;
  }

  public Collection<Protos.TaskInfo> getTasksToDecommission() {
    return tasksToDecommission;
  }
}
