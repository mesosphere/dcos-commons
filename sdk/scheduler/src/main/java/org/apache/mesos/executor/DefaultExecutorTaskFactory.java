package org.apache.mesos.executor;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.specification.TaskSpec;

import java.io.IOException;

/**
 * Implements a factory for a generic custom executor.
 */
public class DefaultExecutorTaskFactory implements ExecutorTaskFactory {
    @Override
    public ExecutorTask createTask(Protos.TaskInfo taskInfo, ExecutorDriver executorDriver)
            throws ExecutorTaskException, IOException {
        try {
            TaskSpec.GoalState goalState = TaskUtils.getGoalState(taskInfo);
            boolean exitOnTermination = goalState.equals(TaskSpec.GoalState.RUNNING) ? true : false;
            return ProcessTask.create(executorDriver, taskInfo, exitOnTermination);
        } catch (TaskException | InvalidProtocolBufferException e) {
            throw new ExecutorTaskException(e);
        }
    }
}
