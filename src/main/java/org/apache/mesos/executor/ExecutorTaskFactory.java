package org.apache.mesos.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

import java.util.List;

/**
 * {@code ExecutorTaskFactory}'s implementations are responsible for creating {@code ExecutorTask} objects.
 */
public interface ExecutorTaskFactory {
    /**
     * Creates {@code ExecutorTask} objects that can be launched and managed by {@code CustomExecutor}.
     *
     * @param taskType
     * @param taskInfo
     * @param driver
     * @return {@code ExecutorType}
     */
    ExecutorTask createTask(final String taskType, final Protos.TaskInfo taskInfo, final ExecutorDriver driver)
            throws ExecutorTaskException;

    /**
     * Creates {@code TimedExecutorTask} objects that can be launched and managed by {@code CustomExecutor}.
     *
     * @param taskType
     * @param executorInfo
     * @return {@code ExecutorType}
     */
    List<TimedExecutorTask> createTimedTasks(
            final String taskType,
            final Protos.ExecutorInfo executorInfo,
            final ExecutorDriver driver) throws ExecutorTaskException;
}
