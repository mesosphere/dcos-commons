package org.apache.mesos.scheduler.txnplan;

import java.util.*;

/**
 * Created by dgrnbrg on 6/23/16.
 */
public interface PlanStorageDriver {
    void saveStatusForPlan(PlanStatus status);

    void savePlan(Plan plan);

    void saveSchedulerState(Map<String, Queue<UUID>> planQueue, Set<UUID> runningPlans);

    SchedulerState loadSchedulerState();

    Map<UUID,Plan> loadPlans();

    PlanStatus tryLoadPlanStatus(UUID id);
}
