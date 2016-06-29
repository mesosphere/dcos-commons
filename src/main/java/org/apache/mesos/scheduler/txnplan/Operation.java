package org.apache.mesos.scheduler.txnplan;

import org.apache.mesos.scheduler.registry.TaskRegistry;

import java.util.Collection;

/**
 * An {@code Operation} represents a single action taken
 * as part of a plan to change the cluster state.
 *
 * {@code Operation} has several methods which govern the
 * behavior of the system: it must validate that the system
 * is in a valid, expected state to perform its action; it
 * must do its best to perform its action; lastly, it must be
 * able to roll itself back.
 *
 * Each operation is given a flexible {@code OperationDriver},
 * which can save and load arbitrary binary data on behalf of
 * the operation. This is useful for internal record keeping,
 * to simplify the implementation of {@code unravel} (in case
 * your {@code Operation} has multiple steps), or to simplify
 * the implementation of {@code doAction}, so that you can pick
 * up where you left off after a crash.
 */

//TODO there should be a way to specify a timeout on operations to ensure progress
public interface Operation {
    /**
     * This method implements the action of this operation.
     * You can put any behavior you'd like here, including
     * blocking operations.
     * @param driver
     */
    void doAction(TaskRegistry registry, OperationDriver driver) throws Exception;

    /**
     * This method does the inverse of this operation's action.
     * For example, if this operation creates a task and reserves
     * resources for it, unravel should terminate that task and
     * release the resources.
     * @param driver
     */
    void unravel(TaskRegistry registry, OperationDriver driver) throws Exception;


    /**
     * In order to avoid conflicts during the execution of operations,
     * each operation must return a list of the names of the executors
     * that it may interact with, to ensure conflicting operations don't
     * occur.
     * @return Collection of names of executors which must be exclusively locked for this operation
     */
    Collection<String> lockedTasks();
}
