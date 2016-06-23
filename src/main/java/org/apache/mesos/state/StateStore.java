package org.apache.mesos.state;

import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * This interface should be implemented in order to store and fetch TaskInfo
 * and TaskStatus information.
 *
 * TaskInfo objects should be persisted when a Task is launched or when reserved
 * resources associated with a potential future Task launch should be recorded.
 *
 * TaskStatus is reported by Mesos to Frameworks at various points including at Task
 * Reconciliation and when Tasks change state.  The TaskStatus of a Task should be
 * recorded so that the state of a Framework's Tasks can be queried.
 */
public interface StateStore {

    /**
     * Stores the FrameworkID for a framework so on Scheduler restart re-registration
     * may occur.
     *
     * @param fwkId FrameworkID to be store
     * @throws StateStoreException when storing the FrameworkID fails
     */
    void storeFrameworkId(Protos.FrameworkID fwkId) throws StateStoreException;


    /**
     * Fetches the previously stored FrameworkID.
     *
     * @return The previously stored FrameworkID
     * @throws StateStoreException if no data was found (not set yet), or when fetching the
     *                             FrameworkID otherwise fails
     */
    Protos.FrameworkID fetchFrameworkId() throws StateStoreException;


    /**
     * Removes any previously stored FrameworkID or does nothing if no FrameworkID
     * was previously stored.
     *
     * @throws StateStoreException when clearing a FrameworkID fails
     */
    void clearFrameworkId() throws StateStoreException;


    /**
     * Stores TaskInfo objects representing tasks in the sub-path of the Executor
     * which launched or will launch the Task. Each TaskInfo must include the following information:
     *
     * <ul>
     * <li>TaskInfo.name (required by proto)</li>
     * <li>TaskInfo.executor.name, or TaskInfo.command should be set to indicate a command executor,
     * in which case TaskInfo.name is used as the executor name</li>
     * </ul>
     *
     * @param tasks Tasks to be stored, which each meet the above requirements
     * @throws StateStoreException when persisting TaskInfo information fails
     */
    void storeTasks(Collection<Protos.TaskInfo> tasks) throws StateStoreException;


    /**
     * Fetches all TaskInfos associated with a particular Executor Name. In the case of command
     * executors, this returns at most one TaskInfo for the provided task name.
     *
     * @param execName The name of the Executor associated with some Tasks
     * @return The TaskInfo objects associated with the indicated Executor
     * @see #fetchExecutorNames() for a list of all executor names
     * @throws StateStoreException if no data was found for the requested Executor, or if fetching
     *                             the TaskInfo information otherwise fails
     */
    Collection<Protos.TaskInfo> fetchTasks(String execName) throws StateStoreException;


    /**
     * Fetches the TaskInfo of a particular Task.
     *
     * @param taskName The name of the Task
     * @param execName The name of the Executor associated with the Task. If a command executor is
     *                 being used, this should be equal to taskName
     * @return The corresponding TaskInfo object
     * @throws StateStoreException if no data was found for the requested Task, or if fetching the
     *                             TaskInfo information otherwise fails
     */
    Protos.TaskInfo fetchTask(String taskName, String execName) throws StateStoreException;


    /**
     * Fetches all the Executor names stored so far. In the case of command executors, this returns
     * the names of the tasks.
     *
     * @return All the Executor names stored so far, or an empty list if none are found
     * @throws StateStoreException when fetching the data fails
     */
    Collection<String> fetchExecutorNames() throws StateStoreException;


    /**
     * Stores the TaskStatus of a particular Task. It must include the following information:
     *
     * <ul>
     * <li>TaskStatus.task_id (required by proto)</li>
     * <li>TaskStatus.executor_id on the initial status update, optional thereafter</li>
     * </ul>
     *
     * @param status The status to be stored, which meets the above requirements
     * @throws StateStoreException when storing the TaskStatus fails
     */
    void storeStatus(Protos.TaskStatus status) throws StateStoreException;


    /**
     * Fetches the TaskStatuses of all Tasks associated with a particular Executor.
     *
     * @param execName The name of the Executor associated with some Tasks
     * @return The TaskStatus objects associated with all tasks for the indicated Executor
     * @throws StateStoreException if no data was found for the requested Executor, or if fetching
     *                             the TaskStatus information otherwise fails
     */
    Collection<Protos.TaskStatus> fetchStatuses(String execName) throws StateStoreException;


    /**
     * Fetches the TaskStatus of a particular Task.
     *
     * @param taskName The name of the Task which should have its status retrieved
     * @param execName The name of the Executor associated with the indicated Task
     * @return The TaskStatus associated with a particular Task
     * @throws StateStoreException if no data was found for the requested Task, or if fetching the
     *                             TaskStatus information otherwise fails
     */
    Protos.TaskStatus fetchStatus(String taskName, String execName) throws StateStoreException;


    /**
     * Removes all data associated with a particular Executor including all stored TaskInfos.
     * and TaskStatuses
     *
     * @param execName The name of the Executor to be cleared
     * @throws StateStoreException when clearing the indicated Executor's informations fails
     */
    void clearExecutor(String execName) throws StateStoreException;
}
