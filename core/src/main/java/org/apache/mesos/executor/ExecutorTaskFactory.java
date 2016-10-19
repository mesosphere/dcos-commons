package org.apache.mesos.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

/**
 * {@code ExecutorTaskFactory}'s implementations are responsible for creating {@code ExecutorTask} objects.
 */
public interface ExecutorTaskFactory {
    /**
     * Creates {@code ExecutorTask} objects that can be launched and managed by {@code CustomExecutor}.
     *
     * @param taskInfo
     * @param driver
     * @return {@code ExecutorType}
     */
    ExecutorTask createTask(final Protos.TaskInfo taskInfo, final ExecutorDriver driver)
            throws ExecutorTaskException;
}
