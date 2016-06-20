package org.apache.mesos.scheduler.txnplan.example;

import org.apache.mesos.scheduler.txnplan.ops.WaitOp;

import java.util.concurrent.TimeUnit;

/**
 * Created by dgrnbrg on 6/20/16.
 */
public class StatusCheckOp extends WaitOp {
    private final long startTime;

    public StatusCheckOp() {
        super(3, TimeUnit.SECONDS);
        startTime = System.currentTimeMillis();
    }

    @Override
    protected boolean isDone() {
        return System.currentTimeMillis() > startTime + 1000*30;
    }
}
