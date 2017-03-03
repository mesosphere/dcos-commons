package com.mesosphere.sdk.state;

import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskStatus;

import java.util.Collection;
import java.util.Optional;

/**
 * A {@code StateStore} stores the state of the frameworks, including tasks' TaskInfo and TaskStatus objects. Each
 * distinct Task is expected to have a unique Task Name, determined by the framework developer.
 *
 * This interface exposes only methods which perform read operations on the StateStore.
 * <p>
 * TaskInfo objects should be persisted when a Task is launched or when reserved resources associated with a potential
 * future Task launch should be recorded.
 * <p>
 * TaskStatus is reported by Mesos to Frameworks at various points including at Task Reconciliation and when Tasks
 * change state.  The TaskStatus of a Task should be recorded so that the state of a Framework's Tasks can be queried.
 */
public interface StateStoreReadOnly {

    // Read Framework ID


    /**
     * Fetches the previously stored FrameworkID, or returns an empty Optional if no FrameworkId was previously stored.
     *
     * @return The previously stored FrameworkID, or an empty Optional indicating the FrameworkID has not been set.
     * @throws StateStoreException when fetching the FrameworkID fails
     */
    Optional<FrameworkID> fetchFrameworkId() throws StateStoreException;


    // Read Tasks


    /**
     * Fetches all the Task names listed in the underlying storage. Note that these should always have a TaskInfo, but
     * may lack TaskStatus.
     *
     * @return All the Task names stored so far, or an empty list if none are found
     * @throws StateStoreException when fetching the data fails
     */
    Collection<String> fetchTaskNames() throws StateStoreException;


    /**
     * Fetches and returns all {@link TaskInfo}s from the underlying storage, or an empty list if none are found. This
     * list should be a superset of the list returned by {@link #fetchStatuses()}.
     *
     * @return All TaskInfos
     * @throws StateStoreException if fetching the TaskInfo information otherwise fails
     */
    Collection<TaskInfo> fetchTasks() throws StateStoreException;


    /**
     * Fetches the TaskInfo for a particular Task, or returns an empty Optional if no matching task is found.
     *
     * @param taskName The name of the Task
     * @return The corresponding TaskInfo object
     * @throws StateStoreException if no data was found for the requested name, or if fetching the TaskInfo otherwise
     *                             fails
     */
    Optional<TaskInfo> fetchTask(String taskName) throws StateStoreException;


    /**
     * Fetches all {@link TaskStatus}es from the underlying storage, or an empty list if none are found. Note that this
     * list may have fewer entries than {@link #fetchTasks()} if some tasks are lacking statuses.
     *
     * @return The TaskStatus objects associated with all tasks
     * @throws StateStoreException if fetching the TaskStatus information fails
     */
    Collection<TaskStatus> fetchStatuses() throws StateStoreException;


    /**
     * Fetches the TaskStatus for a particular Task, or returns an empty Optional if no matching status is found.
     * A given task may sometimes have {@link TaskInfo} while lacking {@link TaskStatus}.
     *
     * @param taskName The name of the Task which should have its status retrieved
     * @return The TaskStatus associated with a particular Task
     * @throws StateStoreException if no data was found for the requested name, or if fetching the TaskStatus
     *                             information otherwise fails
     */
    Optional<TaskStatus> fetchStatus(String taskName) throws StateStoreException;


    // Read Properties


    /**
     * Fetches the value byte array, stored against the Property {@code key}, or throws an error if no matching {@code
     * key} is found.
     *
     * @param key must be a non-blank String without any forward slashes ('/')
     * @throws StateStoreException if no data was found for the requested key, or if fetching the data otherwise fails
     * @see StateStoreUtils#validateKey(String)
     */
    byte[] fetchProperty(String key) throws StateStoreException;

    /**
     * Fetches the list of Property keys, or an empty list if none are found.
     *
     * @throws StateStoreException if fetching the list otherwise fails
     */
    Collection<String> fetchPropertyKeys() throws StateStoreException;


    /**
     * @return true if the offers are suppressed.
     * @throws StateStoreException
     */
    boolean isSuppressed() throws StateStoreException;
}
