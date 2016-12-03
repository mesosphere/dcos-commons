package com.mesosphere.sdk.executor;

import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;

/**
 * The interface for the creation of ExecutorDrivers. This is necessary for
 * dependency injection.
 */
public interface ExecutorDriverFactory {

    /**
     * Gets a driver.
     * @param executor The Executor for which the driver will be returned.
     * @return The ExecutorDriver for executor
     */
    ExecutorDriver getDriver(Executor executor);
}
