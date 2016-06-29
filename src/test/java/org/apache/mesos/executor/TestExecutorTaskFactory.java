package org.apache.mesos.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TestExecutorTaskFactory implements ExecutorTaskFactory {
    private Duration sleep;
    private Duration timeout;
    private boolean onRegistered;
    private boolean onReregistered;

    public TestExecutorTaskFactory(Duration sleep, Duration timeout, boolean onRegistered, boolean onReregistered) {
        this.sleep = sleep;
        this.timeout = timeout;
        this.onRegistered = onRegistered;
        this.onReregistered = onReregistered;
    }

    @Override
    public ExecutorTask createTask(String taskType, Protos.TaskInfo task, ExecutorDriver driver)
            throws ExecutorTaskException {
        if ("TEST".equals(taskType)) {
            return new TestExecutorTask(task, driver);
        }

        return null;
    }

    @Override
    public List<TimedExecutorTask> createTimedTasks(String taskType, Protos.ExecutorInfo executorInfo, ExecutorDriver driver) throws ExecutorTaskException {
        Protos.TaskStatus taskStatus;

        switch(taskType) {
            case DcosTaskConstants.ON_REGISTERED_TASK:
                if (!onRegistered) {
                    return Collections.emptyList();
                }
                taskStatus = getTaskStatus(DcosTaskConstants.ON_REGISTERED_TASK);
                break;
            case DcosTaskConstants.ON_REREGISTERED_TASK:
                if (!onReregistered) {
                    return Collections.emptyList();
                }
                taskStatus = getTaskStatus(DcosTaskConstants.ON_REREGISTERED_TASK);
                break;
            default:
                return Collections.emptyList();
        }

        return Arrays.asList(
                new TestTimedExecutorTask(
                        sleep,
                        timeout,
                        taskStatus,
                        driver));
    }

    public static Protos.TaskStatus getTaskStatus(String taskId) {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue(taskId))
                .setState(Protos.TaskState.TASK_FINISHED)
                .build();
    }
}
