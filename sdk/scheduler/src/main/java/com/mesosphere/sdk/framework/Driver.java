package com.mesosphere.sdk.framework;

import org.apache.mesos.SchedulerDriver;

/**
 * This class provides global access to a SchedulerDriver.
 */
public final class Driver {

    private static SchedulerDriver driver;

    private Driver() {
        // Don't instantiate this class.
    }

    /**
     * Returns the configured {@link SchedulerDriver} client object, or throws an {@link IllegalStateException} if no
     * driver object is available. The returned Driver object should not be retained by the caller, as it may be
     * replaced in the event of a re-registration.
     */
    public static SchedulerDriver getInstance() {
        if (driver == null) {
            throw new IllegalStateException("INTERNAL ERROR: No driver available");
        }
        return driver;
    }

    /**
     * Assigns the driver object to be returned by calls to {@link #getInstance()}. In practice, this should be invoked
     * when the scheduler first registers with Mesos, and/or following a re-registration.
     */
    public static void setDriver(SchedulerDriver driver) {
        Driver.driver = driver;
    }
}
