package org.apache.mesos.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

import java.time.Duration;

/**
 * This class is a Test implementation fo the TimedExecutorTask interface.
 */
public class TestTimedExecutorTask implements TimedExecutorTask {
    private TestExecutorTask testExecutorTask;
    private Duration timeout;

    public TestTimedExecutorTask(Duration sleepDuration, Duration timeout, Protos.TaskStatus taskStatus, ExecutorDriver driver) {
        this.timeout = timeout;
        this.testExecutorTask = new TestExecutorTask(sleepDuration, taskStatus, driver);
    }

    @Override
    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public void stop() {
        testExecutorTask.stop();
    }

    @Override
    public void run() {
        testExecutorTask.run();
    }
}
