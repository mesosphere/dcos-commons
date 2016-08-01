package org.apache.mesos.scheduler.repair.monitor;

import org.apache.mesos.Protos;

/**
 * A special {@link FailureMonitor} that never fails tasks.
 *
 * This is equivalent to disabling the failure detection feature.
 */
public class NeverFailureMonitor implements FailureMonitor {

    @Override
    public boolean hasFailed(Protos.TaskInfo task) {
        return false;
    }
}
