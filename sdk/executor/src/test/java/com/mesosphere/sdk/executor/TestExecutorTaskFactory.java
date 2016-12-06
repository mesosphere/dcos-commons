package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

public class TestExecutorTaskFactory implements ExecutorTaskFactory {
    @Override
    public ExecutorTask createTask(Protos.TaskInfo task, ExecutorDriver driver)
            throws ExecutorTaskException {
            return new TestExecutorTask(task, driver);
    }

    public static Protos.TaskStatus getTaskStatus(String taskId) {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue(taskId))
                .setState(Protos.TaskState.TASK_FINISHED)
                .build();
    }
}
