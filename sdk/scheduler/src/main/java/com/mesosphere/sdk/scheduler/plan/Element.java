package com.mesosphere.sdk.scheduler.plan;

import org.apache.mesos.Protos.TaskStatus;
import com.mesosphere.sdk.scheduler.Observable;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;

import java.util.List;
import java.util.UUID;

/**
 * An Element is a generic component in a tree of elements.  It is the basic unit for the description of Plans used to
 * launch Mesos Tasks. {@link Plan}s, @{link Phase}s and {@link Step}s are all implementations of Elements.  Elements
 * have Strategies (see: {@link Strategy} associated with them which describe when child elements should be presented as
 * candidates for execution.
 *
 * @param <C> is the type of child {@link Element}s.
 */
@SuppressWarnings("rawtypes")
public interface Element<C extends Element> extends Observable {
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
     * Gets the children of this Element.
     */
    List<C> getChildren();

    /**
     * Gets the {@link Strategy} applied to the deployment of child Elements.
     */
    Strategy<C> getStrategy();

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
     * Returns a user-visible message describing this Element.
     */
    String getMessage();

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
     * Indicates whether this Element is in progress.
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
     * Indicates whether this Element is waiting for external input to proceed.
     */
    default boolean isWaiting() {
        return getStatus().equals(Status.WAITING);
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
        return isPrepared() || isStarting();
    }
}
