package org.apache.mesos.executor;

import java.time.Duration;

/**
 * This interface allows an ExecutorTask to further define a timeout duration.
 */
public interface TimedExecutorTask extends ExecutorTask {
    Duration getTimeout();
}
