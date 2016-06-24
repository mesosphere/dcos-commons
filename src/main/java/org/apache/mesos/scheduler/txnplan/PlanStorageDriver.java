package org.apache.mesos.scheduler.txnplan;

import java.util.HashMap;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Created by dgrnbrg on 6/23/16.
 */
public interface PlanStorageDriver {
    void saveStatusForPlan(PlanStatus status);

    void savePlan(Plan plan);

    void saveSchedulerState(HashMap<String, Queue<UUID>> planQueue, Set<UUID> runningPlans);
}
