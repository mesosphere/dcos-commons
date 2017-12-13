package com.mesosphere.sdk.executor;

/**
 * All the executor tasks should implement this.
 */
public interface ExecutorTask extends Runnable {
    public void stop();
}
