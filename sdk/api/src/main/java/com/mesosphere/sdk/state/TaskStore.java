package com.mesosphere.sdk.state;

import java.util.Collection;

import org.apache.mesos.Protos;

/**
 * Interface subset of {@code StateStore} which should be visible to API classes.
 *
 * TODO(nickbp): These calls are simply the subset of StateStore calls that API classes happen to invoke. Ideally these
 * should be reduced as API classes are cleaned up.
 */
public interface TaskStore {

    /**
     * Stores the goal state override status of a particular Task. The {@link Protos.TaskInfo} for this exact task MUST
     * have already been written via {@link #storeTasks(Collection)} beforehand.
     *
     * @throws StateStoreException in the event of a storage error
     */
    public void storeGoalOverrideStatus(String taskName, GoalStateOverride.Status status) throws StateStoreException;

    /**
     * Retrieves the goal state override status of a particular task. A lack of override will result in a
     * {@link GoalStateOverride.Status} with {@code override=NONE} and {@code state=NONE}.
     *
     * @throws StateStoreException in the event of a storage error
     */
    public GoalStateOverride.Status fetchGoalOverrideStatus(String taskName) throws StateStoreException;
}
