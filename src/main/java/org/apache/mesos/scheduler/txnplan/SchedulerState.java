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
