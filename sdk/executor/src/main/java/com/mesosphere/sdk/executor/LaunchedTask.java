package com.mesosphere.sdk.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * This class encapsulates an ExecutorTask and the Future returned when it was started.
 */
public class LaunchedTask {
    private final ExecutorTask executorTask;
    private final Future<?> future;

    /**
     * Starts the provided {@link ExecutorTask} against the provided {@link ExecutorService}.
     */
    public LaunchedTask(ExecutorTask executorTask, ExecutorService executorService) {
        this.executorTask = executorTask;
        this.future = executorService.submit(executorTask);
    }

    public void stop() {
        executorTask.stop();
    }

    public boolean isDone() {
        return future.isDone();
    }
}
