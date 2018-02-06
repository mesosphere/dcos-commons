package com.mesosphere.sdk.scheduler.recovery;

import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * Exposes simple task-oriented API for components that need to interact with the framework as a whole.
 */
public interface TaskFailureListener {
    /**
     * Handles task failure notifications.
     *
     * @param taskInfos The tasks which have failed
     */
    void tasksFailed(Collection<Protos.TaskInfo> taskInfos);
}
