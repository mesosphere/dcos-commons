package org.apache.mesos.scheduler.txnplan;

import java.util.UUID;

/**
 * Created by dgrnbrg on 6/23/16.
 */
public interface PlanListener {
    void stepBegan(Plan plan, PlanStatus status, Step step);
    void stepEnded(Plan plan, PlanStatus status, Step step);
    void planStarted(Plan plan, PlanStatus status);
    void planEnded(Plan plan, PlanStatus status, boolean succeeded);
}
