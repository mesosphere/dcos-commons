package org.apache.mesos.scheduler.txnplan;


import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Helper object to return the entire scheduler state from the {@link PlanStorageDriver}
 * Created by dgrnbrg on 6/24/16.
 */
public final class SchedulerState {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SchedulerState that = (SchedulerState) o;

        if (!planQueue.equals(that.planQueue)) return false;
        return runningPlans.equals(that.runningPlans);

    }

    @Override
    public int hashCode() {
        int result = planQueue.hashCode();
        result = 31 * result + runningPlans.hashCode();
        return result;
    }

    private final Map<String, Queue<UUID>> planQueue;
    private final Set<UUID> runningPlans;

    public SchedulerState(Map<String, Queue<UUID>> planQueue, Set<UUID> runningPlans) {
        this.planQueue = planQueue;
        this.runningPlans = runningPlans;
    }

    public Set<UUID> getRunningPlans() {
        return runningPlans;
    }

    public Map<String, Queue<UUID>> getPlanQueue() {
        return planQueue;
    }
}
