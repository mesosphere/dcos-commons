package com.mesosphere.sdk.executor;

import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.TerminationPolicy;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

/**
 * Implements a factory for a generic custom executor.
 */
public class DefaultExecutorTaskFactory implements ExecutorTaskFactory {
    @Override
    public ExecutorTask createTask(Protos.TaskInfo taskInfo, ExecutorDriver executorDriver)
            throws ExecutorTaskException {
        try {
            return ProcessTask.create(
                    executorDriver,
                    taskInfo,
                    CommonTaskUtils.getTerminationPolicy(taskInfo) == TerminationPolicy.KILL_EXECUTOR);
        } catch (TaskException e) {
            throw new ExecutorTaskException(e);
        }
    }
}
