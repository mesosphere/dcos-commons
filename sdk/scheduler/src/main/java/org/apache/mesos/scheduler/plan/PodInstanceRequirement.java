package org.apache.mesos.scheduler.plan;

import com.mesosphere.sdk.specification.PodInstance;

import java.util.Collection;

/**
 * A PodInstanceRequirement encapsulates a PodInstance and the tasks that should be launched in it.
 */
public class PodInstanceRequirement {
    private final PodInstance podInstance;
    private final Collection<String> tasksToLaunch;

    public PodInstanceRequirement(PodInstance podInstance, Collection<String> tasksToLaunch) {
        this.podInstance = podInstance;
        this.tasksToLaunch = tasksToLaunch;
    }

    public PodInstance getPodInstance() {
        return podInstance;
    }

    public Collection<String> getTasksToLaunch() {
        return tasksToLaunch;
    }
}
