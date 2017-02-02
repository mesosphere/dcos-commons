package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.specification.PodInstance;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * A PodInstanceRequirement encapsulates a {@link PodInstance} and the names of tasks that should be launched in it.
 */
public class PodInstanceRequirement {
    private final PodInstance podInstance;
    private final Collection<String> tasksToLaunch;
    private final Map<String, String> environment;
    private final boolean isPermanentReplacement;

    /**
     * Creates a new instance which isn't marked as a permanent replacement.
     */
    public static PodInstanceRequirement create(
            PodInstance podInstance,
            Collection<String> tasksToLaunch) {
        return new PodInstanceRequirement(podInstance, tasksToLaunch, null, false);
    }

    /**
     * Creates a new instance with each task's environment extended by the provided envvar name-to-value map that is not
     * a permanent replacement.
     */
    public static PodInstanceRequirement create(
            PodInstance podInstance,
            Collection<String> tasksToLaunch,
            Map<String, String> environment) {
        return new PodInstanceRequirement(podInstance, tasksToLaunch, environment, false);
    }

    /**
     * Creates a new instance which is marked as a permanent replacement.
     */
    public static PodInstanceRequirement createPermanentReplacement(
            PodInstance podInstance,
            Collection<String> tasksToLaunch) {
        return new PodInstanceRequirement(podInstance, tasksToLaunch, null, true);
    }

    /**
     * Returns this same instance, but with the supplied environment variable map applied to each task in this pod.
     */
    public PodInstanceRequirement withParameters(Map<String, String> parameters) {
        return new PodInstanceRequirement(getPodInstance(), getTasksToLaunch(), parameters, isPermanentReplacement());
    }

    /**
     * Creates a new instance with the provided permanent replacement setting.
     */
    private PodInstanceRequirement(
            PodInstance podInstance,
            Collection<String> tasksToLaunch,
            Map<String, String> environment,
            boolean isPermanentReplacement) {
        this.podInstance = podInstance;
        this.tasksToLaunch = tasksToLaunch;
        this.environment = environment;
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
     * Returns the map of environment variable names to values that extend the existing environments of tasks in this
     * pod.
     */
    public Map<String, String> getEnvironment() {
        return environment == null ? Collections.emptyMap() : environment;
    }

    /**
     * Returns whether this pod is replacing a previous instance that failed permanently. In this case new resources
     * should be created, even though (now failed) TaskInfos are still present in the StateStore.
     */
    public boolean isPermanentReplacement() {
        return isPermanentReplacement;
    }
}
