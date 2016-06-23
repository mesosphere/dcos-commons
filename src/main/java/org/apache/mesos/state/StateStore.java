package org.apache.mesos.state;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskUtils;

import java.util.Collection;

/**
 * This interface should be implemented in order to store and fetch TaskInfo
 * and TaskStatus information on a per Task basis. Each distinct Task is expected to
 * have a unique Task Name, determined by the framework developer.
 *
 * TaskInfo objects should be persisted when a Task is launched or when reserved
 * resources associated with a potential future Task launch should be recorded.
 *
 * TaskStatus is reported by Mesos to Frameworks at various points including at Task
 * Reconciliation and when Tasks change state.  The TaskStatus of a Task should be
 * recorded so that the state of a Framework's Tasks can be queried.
 */
public interface StateStore {


    // Write Framework ID


    /**
     * Stores the FrameworkID for a framework so on Scheduler restart re-registration
     * may occur.
     *
     * @param fwkId FrameworkID to be store
     * @throws StateStoreException when storing the FrameworkID fails
     */
    void storeFrameworkId(Protos.FrameworkID fwkId) throws StateStoreException;


    /**
     * Removes any previously stored FrameworkID or does nothing if no FrameworkID
     * was previously stored.
     *
     * @throws StateStoreException when clearing a FrameworkID fails
     */
    void clearFrameworkId() throws StateStoreException;


    // Read Framework ID


    /**
     * Fetches the previously stored FrameworkID.
     *
     * @return The previously stored FrameworkID
     * @throws StateStoreException if no data was found (not set yet), or when fetching the
     *                             FrameworkID otherwise fails
     */
    Protos.FrameworkID fetchFrameworkId() throws StateStoreException;


    // Write Tasks


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
     * Stores the TaskStatus of a particular Task. It must include a valid TaskStatus.task_id value,
     * as produced by {@link TaskUtils#toTaskId(String)}.
     *
     * @param status The status to be stored, which meets the above requirement
     * @throws StateStoreException if storing the TaskStatus fails
     */
    void storeStatus(Protos.TaskStatus status) throws StateStoreException;


    /**
     * Removes all data associated with a particular Task including any stored TaskInfo and/or
     * TaskStatus.
     *
     * @param taskName The name of the task to be cleared
     * @throws StateStoreException when clearing the indicated Executor's informations fails
     */
    void clearTask(String taskName) throws StateStoreException;


    // Read Tasks


    /**
     * Fetches all the Task names listed in the underlying storage. Note that these may lack either
     * TaskInfo or TaskStatus (but not both).
     *
     * @return All the Executor names stored so far, or an empty list if none are found
     * @throws StateStoreException when fetching the data fails
     */
    Collection<String> fetchTaskNames() throws StateStoreException;


    /**
     * Fetches and returns all {@link TaskInfo}s from the underlying storage, or an empty list if
     * none are found.
     *
     * @return All TaskInfos
     * @throws StateStoreException if fetching
     *                             the TaskInfo information otherwise fails
     */
    Collection<Protos.TaskInfo> fetchTasks() throws StateStoreException;


    /**
     * Fetches the TaskInfo for a particular Task, or an error if no matching task is found.
     *
     * @param taskName The name of the Task
     * @return The corresponding TaskInfo object
     * @throws StateStoreException if no data was found for the requested name, or if fetching the
     *                             TaskInfo otherwise fails
     */
    Protos.TaskInfo fetchTask(String taskName) throws StateStoreException;


    /**
     * Fetches all {@link TaskStatus}es from the underlying storage.
     *
     * @return The TaskStatus objects associated with all tasks for the indicated Executor
     * @throws StateStoreException if no data was found for the requested Executor, or if fetching
     *                             the TaskStatus information otherwise fails
     */
    Collection<Protos.TaskStatus> fetchStatuses() throws StateStoreException;


    /**
     * Fetches the TaskStatus for a particular Task, or an error if no matching status is found.
     *
     * @param taskName The name of the Task which should have its status retrieved
     * @return The TaskStatus associated with a particular Task
     * @throws StateStoreException if no data was found for the requested Task, or if fetching the
     *                             TaskStatus information otherwise fails
     */
    Protos.TaskStatus fetchStatus(String taskName) throws StateStoreException;
}
