package org.apache.mesos.scheduler.txnplan;

import java.util.UUID;

/**
 * Created by dgrnbrg on 6/22/16.
 */
public interface OperationDriverFactory {
    /**
     * Returns a driver that's customized for the given step.
     * The driver can be assumed to always be accessed in a threadsafe manner.
     * Note that makeDriver may be called multiple times for the same step!
     * @param step The step to specialize a driver for
     * @return
     */
    OperationDriver makeDriver(Step step);
}
