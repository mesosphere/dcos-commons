package com.mesosphere.sdk.executor;

import java.util.concurrent.Future;

/**
 * All the executor tasks should implement this.
 */
public interface ExecutorTask extends Runnable {
    void stop(Future<?> future);
}
