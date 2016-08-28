package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;

import java.util.List;
import java.util.Observer;
import java.util.UUID;

/**
 * Interface for StageManagers.
 */
public interface PlanManager extends Observer {

    Plan getPlan();

    void setPlan(Plan plan);

    Phase getCurrentPhase();

    Block getCurrentBlock();

    boolean isComplete();

    void proceed();

    void interrupt();

    boolean isInterrupted();

    void restart(UUID phaseId, UUID blockId);

    void forceComplete(UUID phaseId, UUID blockId);

    void update(Protos.TaskStatus status);

    boolean hasDecisionPoint(Block block);

    Status getStatus();

    Status getPhaseStatus(UUID phaseId);

    List<String> getErrors();
}
