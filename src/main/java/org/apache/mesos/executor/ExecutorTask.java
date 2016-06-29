package org.apache.mesos.executor;

/**
 * All the executor tasks should implement this.
 */
public interface ExecutorTask extends Runnable {
    void stop();
}
