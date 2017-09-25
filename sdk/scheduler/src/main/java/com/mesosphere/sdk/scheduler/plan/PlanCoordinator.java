package com.mesosphere.sdk.scheduler.plan;

import java.util.Collection;
import java.util.List;

/**
 * PlanCoordinator's job is to coordinate offers among configured {@link PlanManager}s.
 */
public interface PlanCoordinator {

    /**
     * @return The {@link Step}s which are eligible for processing.
     */
    List<Step> getCandidates();

    /**
     * @return The {@link PlanManager}s which the PlanCoordinator coordinates.
     */
    Collection<PlanManager> getPlanManagers();
}
