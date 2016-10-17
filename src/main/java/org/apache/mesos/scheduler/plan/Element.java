package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.scheduler.Observable;
import org.apache.mesos.scheduler.plan.strategy.Strategy;

import java.util.List;
import java.util.UUID;

/**
 * Created by gabriel on 10/15/16.
 * @param <C> is the type of child {@link Element}s.
 */
public interface Element<C extends Element> extends Observable {
    /**
     * Returns the unique identifier of the block.
     */
    UUID getId();

    /**
     * Returns a user-visible name describing the purpose of the Block.
     */
    String getName();

    Status getStatus();

    void setStatus(Status status);

    List<Element> getChildren();

    Strategy<C> getStrategy();

    /**
     * Provides the Block with a recent {@link TaskStatus} update which was received from
     * Mesos.
     */
    void update(TaskStatus status);

    /**
     * Forcefully restarts the block, putting it into a PENDING state, waiting to be resumed with
     * a call to {@link start()}.
     */
    default void restart() {
        setStatus(Status.PENDING);
    }

    /**
     * Forcefully marks the block as COMPLETE, cancelling any work that hasn't started or that's
     * currently in progress.
     */
    default void forceComplete() {
        setStatus(Status.COMPLETE);
    }

    /**
     * Returns a user-visible message describing the current status of the Block.
     */
    String getMessage();

    /**
     * Returns a list of user-visible descriptive error messages which have been encountered while
     * progressing through this Plan. A non-empty response implies that the Plan is in an ERROR
     * state.
     */
    List<String> getErrors();

    default boolean hasErrors() {
        return getStatus().equals(Status.ERROR);
    }

    default boolean isPending() {
        return getStatus().equals(Status.PENDING);
    }

    default boolean isInProgress() {
        return getStatus().equals(Status.IN_PROGRESS);
    }

    default boolean isWaiting() {
        return getStatus().equals(Status.WAITING);
    }

    default boolean isComplete() {
        return getStatus().equals(Status.COMPLETE);
    }
}
