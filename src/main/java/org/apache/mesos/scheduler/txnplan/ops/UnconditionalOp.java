package org.apache.mesos.scheduler.txnplan.ops;

import org.apache.mesos.scheduler.registry.TaskRegistry;
import org.apache.mesos.scheduler.txnplan.Operation;
import org.apache.mesos.scheduler.txnplan.OperationDriver;

import java.util.Collection;

/**
 * Operation which does a {@code Runnable} action with
 * no preconditions and no roll back logic.
 */
public abstract class UnconditionalOp implements Operation {
    private final Collection<String> executors;

    private UnconditionalOp() {
        executors = null;
    }

    protected UnconditionalOp(Collection<String> executors) {
        this.executors = executors;
    }

    @Override
    public void unravel(TaskRegistry registry, OperationDriver driver) {
        driver.info("Rolling back unconditional operation; this is a no-op");
    }

    @Override
    public Collection<String> lockedTasks() {
        return executors;
    }
}
