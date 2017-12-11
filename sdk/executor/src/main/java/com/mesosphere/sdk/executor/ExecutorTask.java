package com.mesosphere.sdk.executor;

import java.util.concurrent.Future;

/**
 * All the executor tasks should implement this.
 */
public interface ExecutorTask extends Runnable {
    public void stop(Future<?> future);
}
