package com.mesosphere.sdk.scheduler.plan;

/**
 * This interface allows an opportunity for plans to be modified before execution.
 */
public interface PlanCustomizer {

    /**
     * Takes a reference to a plan, returning a reference to a modified plan derived from the original.
     *
     * Note: ALL plans (including the uninstall deploy plan) are run through the customizer if one is declared.
     * @param plan
     * @return
     */
    Plan updatePlan(Plan plan);
}
