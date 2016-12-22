package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.specification.PodInstance;

import java.util.Collection;

/**
 * A PodInstanceRequirement encapsulates a {@link PodInstance} and the names of tasks that should be launched in it.
 */
public class PodInstanceRequirement {
    private final PodInstance podInstance;
    private final Collection<String> tasksToLaunch;

    public PodInstanceRequirement(PodInstance podInstance, Collection<String> tasksToLaunch) {
        this.podInstance = podInstance;
        this.tasksToLaunch = tasksToLaunch;
    }

    /**
     * Returns the definition of the pod instance to be created.
     */
    public PodInstance getPodInstance() {
        return podInstance;
    }

    /**
     * Returns the list of tasks to be launched within this pod. This doesn't necessarily match the tasks listed in the
     * {@link PodInstance}.
     */
    public Collection<String> getTasksToLaunch() {
        return tasksToLaunch;
    }
}
