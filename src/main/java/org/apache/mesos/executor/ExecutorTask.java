package org.apache.mesos.executor;

/**
 * All the executor tasks should implement this.
 */
public abstract class ExecutorTask implements Runnable {
    public static final String TASK_TYPE = "TASK_TYPE";

    @Override
    public void run() {
        start();
        checkHealth();
    }

    public abstract void start();

    public abstract boolean checkHealth();

    public abstract void stop();
}
