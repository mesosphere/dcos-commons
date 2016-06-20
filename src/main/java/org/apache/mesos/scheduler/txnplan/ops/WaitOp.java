package org.apache.mesos.scheduler.txnplan.ops;

import org.apache.mesos.scheduler.registry.TaskRegistry;
import org.apache.mesos.scheduler.txnplan.Operation;
import org.apache.mesos.scheduler.txnplan.OperationDriver;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Created by dgrnbrg on 6/20/16.
 */
public abstract class WaitOp implements Operation {
    private final long pauseTime;
    private final TimeUnit pauseTimeUnit;

    private WaitOp() {
        pauseTime = 0;
        pauseTimeUnit = null;
    }

    protected WaitOp(long pauseTime, TimeUnit pauseTimeUnit) {
        this.pauseTime = pauseTime;
        this.pauseTimeUnit = pauseTimeUnit;
    }

    protected abstract boolean isDone();

    @Override
    public void doAction(TaskRegistry registry, OperationDriver driver) throws Exception {
        while (!isDone()) {
            pauseTimeUnit.sleep(pauseTime);
        }
    }

    @Override
    public void rollback(TaskRegistry registry, OperationDriver driver) throws Exception {

    }

    @Override
    public Collection<String> lockedExecutors() {
        return Collections.EMPTY_LIST;
    }
}
