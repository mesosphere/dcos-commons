package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.scheduler.Observable;
import org.apache.mesos.scheduler.plan.strategy.Strategy;

import java.util.List;
import java.util.UUID;

/**
 * An Element is a generic component in a tree of elements.  It is the basic unit for the description of Plans used to
 * launch Mesos Tasks. {@link Plan}s, @{link Phase}s and {@link Block}s are all implementations of Elements.  Elements
 * have Strategies (see: {@link Strategy} associated with them which describe when child elements should be presented as
 * candidates for execution.
 *
 * @param <C> is the type of child {@link Element}s.
 */
public interface Element<C extends Element> extends Observable {
    /**
     * Returns the unique identifier of the element.
     */
    UUID getId();

    /**
     * Returns a user-visible name describing the purpose of an Element.
     */
    String getName();

    /**
     * Returns the Status of an Element.
     */
    Status getStatus();

    /**
     * Sets the Status of an Element.
     */
    void setStatus(Status status);

    /**
     * Gets the children of an Element.
     */
    List<C> getChildren();

    /**
     * Gets the Strategy applied to the deployment of child elements.
     */
    Strategy<C> getStrategy();

    /**
     * Provides the Element with a recent {@link TaskStatus} update which was received from Mesos.
     */
    void update(TaskStatus status);

    /**
     * Forcefully restarts the element by putting it into a PENDING state.
     */
    default void restart() {
        setStatus(Status.PENDING);
    }

    /**
     * Forcefully marks the element as COMPLETE, cancelling any work that hasn't started.
     */
    default void forceComplete() {
        setStatus(Status.COMPLETE);
    }

    /**
     * Returns a user-visible message describing the Element.
     */
    String getMessage();

    /**
     * Returns a list of user-visible descriptive error messages associated with this Element.
     */
    List<String> getErrors();

    /**
     * Indicates whether the Element has encountered errors.
     */
    default boolean hasErrors() {
        return getStatus().equals(Status.ERROR);
    }

    /**
     * Indicates whether the Element has been started.
     */
    default boolean isPending() {
        return getStatus().equals(Status.PENDING);
    }

    /**
     * Indicates whether the Element is in progress.
     */
    default boolean isInProgress() {
        return getStatus().equals(Status.IN_PROGRESS);
    }

    /**
     * Indicates whether the Element is waiting for external input to proceed.
     */
    default boolean isWaiting() {
        return getStatus().equals(Status.WAITING);
    }

    /**
     * Indicates whether the Element is complete.
     */
    default boolean isComplete() {
        return getStatus().equals(Status.COMPLETE);
    }
}
