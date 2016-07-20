package org.apache.mesos.scheduler.txnplan.ops;

import org.apache.mesos.scheduler.registry.TaskRegistry;
import org.apache.mesos.scheduler.txnplan.Operation;
import org.apache.mesos.scheduler.txnplan.OperationDriver;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by dgrnbrg on 7/18/16.
 */
public class SleepOp implements Operation {
    private long startTimeMillis;
    private boolean resumable;
    private long durationSeconds;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SleepOp sleepOp = (SleepOp) o;

        if (startTimeMillis != sleepOp.startTimeMillis) return false;
        if (resumable != sleepOp.resumable) return false;
        return durationSeconds == sleepOp.durationSeconds;

    }

    @Override
    public int hashCode() {
        int result = (int) (startTimeMillis ^ (startTimeMillis >>> 32));
        result = 31 * result + (resumable ? 1 : 0);
        result = 31 * result + (int) (durationSeconds ^ (durationSeconds >>> 32));
        return result;
    }

    protected SleepOp() {}

    public SleepOp(long durationSeconds, boolean resumable) {
        this.startTimeMillis = System.currentTimeMillis();
        this.durationSeconds = durationSeconds;
        this.resumable = resumable;
    }

    @Override
    public void doAction(TaskRegistry registry, OperationDriver driver) throws Exception {
        Integer periodsPassed = resumable ? (Integer) driver.load() : null;
        if (periodsPassed == null) {
            periodsPassed = 0;
            driver.info("Sleeping for " + durationSeconds + " seconds");
        } else {
            driver.info("Resuming sleep, sleeping for " +
                    (durationSeconds - periodsPassed) +
                    " seconds");
        }
        while (durationSeconds > periodsPassed && !shortCircuit(registry, driver)) {
            Thread.sleep(1000);
            periodsPassed++;
            if (resumable) {
                driver.save(periodsPassed);
            }
        }
    }

    /**
     * This method can be overridden to allow frameworks to short-circuit the sleep.
     * By default, sleep operations will never short circuit.
     * @return True if the sleep should be ended, false otherwise
     */
    public boolean shortCircuit(TaskRegistry registry, OperationDriver driver) {
        return false;
    }

    @Override
    public void unravel(TaskRegistry registry, OperationDriver driver) throws Exception {

    }

    @Override
    public Collection<String> lockedTasks() {
        return Collections.singletonList("no-op$" + hashCode());
    }
}
