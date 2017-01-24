package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class provides an encapsulation of common implementations for the methods associated with pausing and resuming
 * strategies.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public abstract class InterruptibleStrategy<C extends Element> implements Strategy<C> {
    private AtomicBoolean interrupted = new AtomicBoolean(false);

    @Override
    public void interrupt() {
        interrupted.set(true);
    }

    @Override
    public void proceed() {
        interrupted.set(false);
    }

    @Override
    public boolean isInterrupted() {
        return interrupted.get();
    }
}
