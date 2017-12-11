package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

/**
 * Implements a factory for a generic custom executor.
 */
public class DefaultExecutorTaskFactory implements ExecutorTaskFactory {
    @Override
    public ExecutorTask createTask(Protos.TaskInfo taskInfo, ExecutorDriver executorDriver) {
        return ProcessTask.create(executorDriver, taskInfo);
    }
}
