package com.mesosphere.sdk.executor;

import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.ExecutorTaskLabelReader;
import com.mesosphere.sdk.specification.GoalState;
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
            GoalState goalState = new ExecutorTaskLabelReader(taskInfo).getGoalState();
            boolean exitOnTermination = goalState.equals(GoalState.RUNNING);
            return ProcessTask.create(executorDriver, taskInfo, exitOnTermination);
        } catch (TaskException e) {
            throw new ExecutorTaskException(e);
        }
    }
}
