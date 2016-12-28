package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Future;

public class TestExecutorTask implements ExecutorTask {
    private Logger LOGGER = LoggerFactory.getLogger(getClass());
    private Duration sleepDuration;
    private Protos.TaskStatus taskStatus;
    private ExecutorDriver driver;

    public TestExecutorTask(
            Duration sleepDuration,
            Protos.TaskStatus taskStatus,
            ExecutorDriver driver) {
        this.sleepDuration = sleepDuration;
        this.taskStatus = taskStatus;
        this.driver = driver;
    }

    public TestExecutorTask(Protos.TaskInfo taskInfo, ExecutorDriver driver) {
        this.taskStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue("test-task-id"))
                .setState(Protos.TaskState.TASK_FINISHED)
                .build();

        this.sleepDuration = Duration.ofMillis(100);
    }

    @Override
    public void run() {
        LOGGER.info("Doing some work");

        try {
            Thread.sleep(sleepDuration.toMillis());
        } catch (InterruptedException e) {
            LOGGER.error("Sleep failed with exception: ", e);
        }

        LOGGER.info("Done with work");
        driver.sendStatusUpdate(taskStatus);
    }

    @Override
    public void stop(Future<?> future) {

    }
}
