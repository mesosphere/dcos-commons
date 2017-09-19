package com.mesosphere.sdk.queue;

import com.mesosphere.sdk.scheduler.plan.Step;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class maintains a set of the current work the plans have generated.  This work takes the form of
 * {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement}s.
 */
public class WorkSet {
    private final Set<Step> set;
    private final Object lock = new Object();
    private static WorkSet workSet;
    private static final AtomicBoolean created = new AtomicBoolean(false);

    public static WorkSet getInstance() {
        if (created.compareAndSet(false, true)) {
            workSet = new WorkSet();
        }

        return workSet;
    }

    private WorkSet() {
        this.set = new HashSet<>();
    }

    public void setWork(Set<Step> work) {
        synchronized (lock) {
            set.clear();
            set.addAll(work);
        }
    }

    public Set<Step> getWork() {
        synchronized (lock) {
            return new HashSet<>(set);
        }
    }
}
