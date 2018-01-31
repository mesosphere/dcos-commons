package com.mesosphere.sdk.scheduler.plan;

/**
 * This interface allows an opportunity for plans to be modified before execution.
 */
public interface PlanCustomizer {
    Plan updatePlan(Plan plan);
}
