package com.mesosphere.sdk.executor;

import java.util.concurrent.Future;

/**
 * This class encapsulates an ExecutorTask and the Future returned whent it was started.
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

    public void stop() {
        executorTask.stop(getFuture());
    }
}
