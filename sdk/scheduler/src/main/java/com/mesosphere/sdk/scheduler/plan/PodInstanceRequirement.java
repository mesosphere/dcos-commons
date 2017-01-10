package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.specification.PodInstance;

import java.util.Collection;

/**
 * A PodInstanceRequirement encapsulates a {@link PodInstance} and the names of tasks that should be launched in it.
 */
public class PodInstanceRequirement {
    private final PodInstance podInstance;
    private final Collection<String> tasksToLaunch;
    private final boolean isPermanentReplacement;

    /**
     * Creates a new instance which isn't marked as a permanent replacement.
     */
    public static PodInstanceRequirement create(
            PodInstance podInstance,
            Collection<String> tasksToLaunch) {
        return new PodInstanceRequirement(podInstance, tasksToLaunch, false);
    }

    /**
     * Creates a new instance which is marked as a permanent replacement.
     */
    public static PodInstanceRequirement createPermanentReplacement(
            PodInstance podInstance,
            Collection<String> tasksToLaunch) {
        return new PodInstanceRequirement(podInstance, tasksToLaunch, true);
    }

    /**
     * Creates a new instance with the provided permanent replacement setting.
     */
    private PodInstanceRequirement(
            PodInstance podInstance,
            Collection<String> tasksToLaunch,
            boolean isPermanentReplacement) {
        this.podInstance = podInstance;
        this.tasksToLaunch = tasksToLaunch;
        this.isPermanentReplacement = isPermanentReplacement;
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

    /**
     * Returns whether this pod is replacing a previous instance that failed permanently. In this case new resources
     * should be created, even though (now failed) TaskInfos are still present in the StateStore.
     */
    public boolean isPermanentReplacement() {
        return isPermanentReplacement;
    }
}
