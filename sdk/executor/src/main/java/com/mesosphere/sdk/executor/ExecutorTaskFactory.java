package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

/**
 * {@code ExecutorTaskFactory}'s implementations are responsible for creating {@code ExecutorTask} objects.
 */
public interface ExecutorTaskFactory {
    /**
     * Creates {@code ExecutorTask} objects that can be launched and managed by {@code CustomExecutor}.
     *
     * @param taskInfo The TaskInfo provided by Mesos' call to
     *                 {@link org.apache.mesos.Executor#launchTask(ExecutorDriver, Protos.TaskInfo)}
     * @param driver The ExecutorDriver provided by Mesos.
     * @return An {@link ExecutorTask} which can be executed.
     */
    ExecutorTask createTask(final Protos.TaskInfo taskInfo, final ExecutorDriver driver);
}
