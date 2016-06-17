package org.apache.mesos.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

public class TestExecutorTaskFactory implements ExecutorTaskFactory {
    @Override
    public ExecutorTask createTask(String taskType, Protos.TaskInfo task, ExecutorDriver driver)
            throws ExecutorTaskException {
        if ("TEST".equals(taskType)) {
            return new TestExecutorTask(task, driver);
        }

        return null;
    }
}
