package com.mesosphere.sdk.executor;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

import java.io.IOException;

/**
 * Implements a factory for a generic custom executor.
 */
public class DefaultExecutorTaskFactory implements ExecutorTaskFactory {
    @Override
    public ExecutorTask createTask(Protos.TaskInfo taskInfo, ExecutorDriver executorDriver)
            throws ExecutorTaskException, IOException {
        try {
            return ProcessTask.create(executorDriver, taskInfo);
        } catch (InvalidProtocolBufferException e) {
            throw new ExecutorTaskException(e);
        }
    }
}
