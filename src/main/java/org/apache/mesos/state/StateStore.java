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
     * Stores TasksInfo objects representing tasks in the sub-path of the Executor
     * which launched or will launch the Task.
     *
     * @param tasks Tasks to be associated persistently with a particular Executor
     * @param execName The name of the Executor hosting the tasks.
     * @throws StateStoreException when persisting TaskInfo information fails
     */
    void storeTasks(Collection<Protos.TaskInfo> tasks, String execName) throws StateStoreException;


    /**
     * Fetches the TaskInfo objects associated with a particular Executor.
     *
     * @param execName The name of the Executor associated with some Tasks
     * @return The TaskInfo objects associated with the indicated Executor
     * @throws StateStoreException if no data was found for the requested Executor, or when fetching
     *                             the TaskInfo information otherwise fails
     */
    Collection<Protos.TaskInfo> fetchTasks(String execName) throws StateStoreException;


    /**
     * Fetches all the Executor names so far stored.
     *
     * @return All the Executor names so far stored, or an empty list if none are found
     * @throws StateStoreException when fetching the data fails
     */
    Collection<String> fetchExecutorNames() throws StateStoreException;


    /**
     * Stores the TaskStatus of a particular Task.
     *
     * @param status The status to be stored
     * @param taskName The name of the Task associated with the indicated status
     * @param execName The name of the Executor which the Task is associated with
     * @throws StateStoreException when storing the TaskStatus fails
     */
    void storeStatus(Protos.TaskStatus status, String taskName, String execName) throws StateStoreException;


    /**
     * Fetches the TaskStatus of a particular Task.
     *
     * @param taskName The name of the Task which should have its status retrieved
     * @param execName The name of the Executor associated with the indicated Task
     * @return The TaskStatus associated with a particular Task
     * @throws StateStoreException if no data was found for the requested Task, or when fetching the
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
