package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.scheduler.instrumentation.LogAndDelegateSchedulerDriver;
import org.apache.mesos.SchedulerDriver;

import java.util.Optional;

/**
 * This class provides global access to a SchedulerDriver.
 */
public final class Driver {
    private static SchedulerDriver driver;
    private static Object lock = new Object();

    private Driver() {
        // Don't instantiate this class.
    }

    public static Optional<SchedulerDriver> getDriver() {
        synchronized (lock) {
            return Optional.ofNullable(driver);
        }
    }

    public static void setDriver(SchedulerDriver driver) {
        synchronized (lock) {
            // TODO(mowsiany): make the log wrapping optional
            Driver.driver = new LogAndDelegateSchedulerDriver(driver);
        }
    }
}
