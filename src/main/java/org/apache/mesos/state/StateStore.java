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
    void storeFrameworkId(Protos.FrameworkID fwkId) throws StateStoreException;
    Protos.FrameworkID fetchFrameworkId() throws StateStoreException;
    void clearFrameworkId() throws StateStoreException;

    void storeTasks(Collection<Protos.TaskInfo> tasks, Protos.ExecutorID execId) throws StateStoreException;
    Collection<Protos.TaskInfo> fetchTasks(Protos.ExecutorID execId) throws StateStoreException;

    void storeStatus(Protos.TaskStatus status, String taskName, Protos.ExecutorID execId) throws StateStoreException;
    Protos.TaskStatus fetchStatus(String taskName, Protos.ExecutorID execId) throws StateStoreException;

    void clearExecutor(Protos.ExecutorID execId) throws StateStoreException;
}
