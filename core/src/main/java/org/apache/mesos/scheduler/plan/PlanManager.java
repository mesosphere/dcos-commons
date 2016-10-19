package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;

import java.util.*;

/**
 * PlanManager is the management interface for {@link Plan}s.
 */
public interface PlanManager extends Observer {

    Plan getPlan();

    void setPlan(Plan plan);

    Optional<Phase> getCurrentPhase();

    /**
     * Determines the next block that should be considered for scheduling. Blocks that are being selected by other
     * PlanManager are provided as {@code dirtiedAssets} as a hint to this PlanManager to assist with scheduling.
     *
     * @param dirtiedAssets Other blocks/assets that are already selected for scheduling.
     * @return An Optional Block that can be scheduled or an empty Optional when there is no block to schedule. For ex:
     *          the chosen block to schedule is being worked on by other PlanManager(s), etc.
     */
    Optional<Block> getCurrentBlock(Collection<String> dirtiedAssets);

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
