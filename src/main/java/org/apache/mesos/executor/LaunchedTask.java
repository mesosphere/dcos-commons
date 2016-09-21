package org.apache.mesos.executor;

import java.util.concurrent.Future;

/**
 * Created by gabriel on 9/21/16.
 */
public class LaunchedTask {
    private final ExecutorTask executorTask;
    private final Future<?> future;

    public LaunchedTask(ExecutorTask executorTask, Future<?> future) {
        this.executorTask = executorTask;
        this.future = future;
    }

    public ExecutorTask getExecutorTask() {
        return executorTask;
    }

    public Future<?> getFuture() {
        return future;
    }
}
