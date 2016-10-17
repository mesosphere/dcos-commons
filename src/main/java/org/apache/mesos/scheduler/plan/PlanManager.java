package org.apache.mesos.scheduler.plan;

import java.util.Collection;

/**
 * PlanManager is the management interface for {@link Plan}s.
 */
public interface PlanManager {
    Plan getPlan();

    /**
     * Determines the next block that should be considered for scheduling. Blocks that are being selected by other
     * PlanManager are provided as {@code dirtiedAssets} as a hint to this PlanManager to assist with scheduling.
     *
     * @param dirtyAssets Other blocks/assets that are already selected for scheduling.
     * @return An Optional Block that can be scheduled or an empty Optional when there is no block to schedule. For ex:
     *          the chosen block to schedule is being worked on by other PlanManager(s), etc.
     */
    Collection<? extends Block> getCandidates(Collection<String> dirtyAssets);
}
