package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestExecutorTask implements ExecutorTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestExecutorTask.class);
    private static final long SLEEP_MILLIS = 100;

    private final Protos.TaskStatus taskStatus;
    private final ExecutorDriver driver;

    public TestExecutorTask(Protos.TaskInfo taskInfo, ExecutorDriver driver) {
        this.taskStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue("test-task-id"))
                .setState(Protos.TaskState.TASK_FINISHED)
                .build();
        this.driver = driver;
    }

    @Override
    public void run() {
        LOGGER.info("Doing some work");

        try {
            Thread.sleep(SLEEP_MILLIS);
        } catch (InterruptedException e) {
            LOGGER.error("Sleep failed with exception: ", e);
        }

        LOGGER.info("Done with work");
        driver.sendStatusUpdate(taskStatus);
    }

    @Override
    public void stop() {

    }
}
