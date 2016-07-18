package org.apache.mesos.scheduler.txnplan;

import java.util.*;

/**
 * TODO: this needs APIs to delete plans
 */
public interface PlanStorageDriver {
    void saveStatusForPlan(PlanStatus status);

    void savePlan(Plan plan);

    void saveSchedulerState(Map<String, Queue<UUID>> planQueue, Set<UUID> runningPlans);

    void deletePlan(UUID planUuid);

    SchedulerState loadSchedulerState();

    Map<UUID,Plan> loadPlans();

    PlanStatus tryLoadPlanStatus(UUID id);
}
