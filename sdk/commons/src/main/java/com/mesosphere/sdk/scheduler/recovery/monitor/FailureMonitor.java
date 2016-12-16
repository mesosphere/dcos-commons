package com.mesosphere.sdk.scheduler.recovery.monitor;

import org.apache.mesos.Protos.TaskInfo;

/**
 * Instances of this class are used to determine when a stopped task has failed and should be restarted elsewhere.
 */
public interface FailureMonitor {
    /**
     * Determines whether the given {@link TaskInfo}has failed forever or not. This hook will be first called with a
     * given {@link TaskInfo} the first time that {@link TaskInfo} stops running. It may be called additional times
     * after that.
     *
     * @param task The {@link TaskInfo} that is no longer running
     * @return true if the {@link TaskInfo} should be considered permanently lost & restarted elsewhere, false if it's
     * machine might still come back
     */
    boolean hasFailed(TaskInfo task);
}
