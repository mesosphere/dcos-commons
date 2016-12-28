package com.mesosphere.sdk.executor;

import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.MesosExecutorDriver;

/**
 * Implements ExecutorDriverFactory to construct MesosExecutorDrivers.
 */
public class MesosExecutorDriverFactory implements ExecutorDriverFactory {

    @Override
    public ExecutorDriver getDriver(Executor executor) {
        return new MesosExecutorDriver(executor);
    }
}
