package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.collections.CollectionUtils;

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
    private final RecoveryType recoveryType;

    /**
     * Creates a new instance.
     */
    public static PodInstanceRequirement create(
            PodInstance podInstance,
            Collection<String> tasksToLaunch) {
        return new PodInstanceRequirement(podInstance, tasksToLaunch, null, RecoveryType.NONE);
    }

    /**
     * Creates a new instance with each task's environment extended by the provided envvar name-to-value map that is not
     * a permanent replacement.
     */
    public static PodInstanceRequirement create(
            PodInstance podInstance,
            Collection<String> tasksToLaunch,
            Map<String, String> environment) {
        return new PodInstanceRequirement(podInstance, tasksToLaunch, environment, RecoveryType.NONE);
    }

    public static PodInstanceRequirement createTransientRecovery(
            PodInstance podInstance,
            Collection<String> tasksToLaunch) {
        return createRecoveryRequirement(podInstance, tasksToLaunch, RecoveryType.TRANSIENT);
    }

    /**
     * Creates a new instance which is marked as a permanent replacement.
     */
    public static PodInstanceRequirement createTransientRecovery(PodInstanceRequirement podInstanceRequirement) {
        return createTransientRecovery(
                podInstanceRequirement.getPodInstance(),
                podInstanceRequirement.getTasksToLaunch());
    }

    /**
     * Creates a new instance which is marked as a permanent replacement.
     */
    public static PodInstanceRequirement createPermanentReplacement(
            PodInstance podInstance,
            Collection<String> tasksToLaunch) {
        return createRecoveryRequirement(podInstance, tasksToLaunch, RecoveryType.PERMANENT);
    }

    private static PodInstanceRequirement createRecoveryRequirement(
            PodInstance podInstance,
            Collection<String> tasksToLaunch,
            RecoveryType recoveryType) {

        return new PodInstanceRequirement(
                podInstance,
                tasksToLaunch,
                null,
                recoveryType);
    }

    /**
     * Creates a new instance which is marked as a permanent replacement.
     */
    public static PodInstanceRequirement createPermanentReplacement(PodInstanceRequirement podInstanceRequirement) {
        return createPermanentReplacement(
                podInstanceRequirement.getPodInstance(),
                podInstanceRequirement.getTasksToLaunch());
    }

    /**
     * Returns this same instance, but with the supplied environment variable map applied to each task in this pod.
     */
    public PodInstanceRequirement withParameters(Map<String, String> environment) {
        return new PodInstanceRequirement(getPodInstance(), getTasksToLaunch(), environment, getRecoveryType());
    }

    /**
     * Creates a new instance with the provided permanent replacement setting.
     */
    private PodInstanceRequirement(
            PodInstance podInstance,
            Collection<String> tasksToLaunch,
            Map<String, String> environment,
            RecoveryType recoveryType) {
        this.podInstance = podInstance;
        this.tasksToLaunch = tasksToLaunch;
        this.environment = environment;
        this.recoveryType = recoveryType;
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

    public RecoveryType getRecoveryType() {
        return recoveryType;
    }

    @Override
    public String toString() {
        return TaskUtils.getStepName(getPodInstance(), getTasksToLaunch());
    }

    public boolean conflicts(PodInstanceRequirement podInstanceRequirement) {
        boolean podConflicts = podInstanceRequirement.getPodInstance().conflicts(getPodInstance());
        boolean tasksConflict = CollectionUtils.isEqualCollection(
                podInstanceRequirement.getTasksToLaunch(),
                getTasksToLaunch());
        return podConflicts && tasksConflict;
    }
}
