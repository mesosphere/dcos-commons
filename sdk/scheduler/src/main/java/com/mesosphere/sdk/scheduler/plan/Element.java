package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import org.apache.mesos.Protos.TaskStatus;
import com.mesosphere.sdk.scheduler.Observable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An Element is a generic component in a tree of elements.  It is the basic unit for the description of Plans used to
 * launch Mesos Tasks. {@link Plan}s, @{link Phase}s and {@link Step}s are all implementations of Elements.  Elements
 * have Strategies (see: {@link Strategy} associated with them which describe when child elements should be presented as
 * candidates for execution.
 */
public interface Element extends Observable {
    /**
     * Returns the unique identifier of this Element.
     */
    UUID getId();

    /**
     * Returns a user-visible name describing the purpose of this Element.
     */
    String getName();

    /**
     * Returns the Status of this Element.
     */
    Status getStatus();

    /**
     * Provides the Element with a recent {@link TaskStatus} update which was received from Mesos.
     */
    void update(TaskStatus status);

    /**
     * Forcefully restarts the element by putting it into a {@link Status#PENDING} state.
     */
    void restart();

    /**
     * Forcefully marks the element as {@link Status#COMPLETE}, cancelling any work that hasn't started.
     */
    void forceComplete();

    /**
     * Returns a list of user-visible descriptive error messages associated with this Element.
     */
    List<String> getErrors();

    /**
     * Indicates whether this Element has encountered errors.
     */
    default boolean hasErrors() {
        return getStatus().equals(Status.ERROR);
    }

    /**
     * Indicates whether this Element has been started.
     */
    default boolean isPending() {
        return getStatus().equals(Status.PENDING);
    }

    /**
     * Indicates whether this Element is prepared.
     */
    default boolean isPrepared() {
        return getStatus().equals(Status.PREPARED);
    }

    /**
     * Indicates whether the Element is starting.
     */
    default boolean isStarting() {
        return getStatus().equals(Status.STARTING);
    }

    /**
     * Indicates whether this Element is complete.
     */
    default boolean isComplete() {
        return getStatus().equals(Status.COMPLETE);
    }

    /**
     * Indicates whether this Element is in progress.
     */
    default boolean isInProgress() {
        return isPrepared() || isStarting() || getStatus().equals(Status.IN_PROGRESS);
    }

    /**
     * Indicates whether this Element is capable of being started.
     */
    default boolean isEligible(Collection<PodInstanceRequirement> dirtyAssets) {
        return !isComplete() && !hasErrors();
    }

    /**
     * Indicates whether this Element is running.
     */
    default boolean isRunning() {
        return getStatus().isRunning();
    }

    /**
     * Provides the Element with a set of named string parameters that it can either use on start or provide to
     * children, if it has any.
     */
    default void updateParameters(Map<String, String> parameters) { }

    /**
     * Returns a reasonable user-visible status message.
     */
    default String getMessage() {
        return String.format("%s: '%s [%s]' has status: '%s'.",
                getClass().getName(), getName(), getId(), getStatus());
    }
}
