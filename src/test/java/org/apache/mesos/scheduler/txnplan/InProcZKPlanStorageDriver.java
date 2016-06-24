package org.apache.mesos.scheduler.txnplan;

import java.util.HashMap;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Created by dgrnbrg on 6/24/16.
 */
public class InProcZKPlanStorageDriver implements PlanStorageDriver {
    @Override
    public void saveStatusForPlan(PlanStatus status) {

    }

    @Override
    public void savePlan(Plan plan) {

    }

    @Override
    public void saveSchedulerState(HashMap<String, Queue<UUID>> planQueue, Set<UUID> runningPlans) {

    }
}
