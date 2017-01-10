package com.mesosphere.sdk.scheduler.recovery;

import org.apache.mesos.Protos.TaskID;

/**
 * Exposes simple task-oriented API for components that need to interact with the framework as a whole.
 */
public interface TaskFailureListener {
    /**
     * Notifies that the task identified by the given {@link TaskID} has failed.
     *
     * @param taskId The ID of the task to delete
     */
    void taskFailed(TaskID taskId);
}
