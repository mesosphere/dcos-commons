package com.mesosphere.sdk.scheduler.plan;

/**
 * This interface allows an opportunity for plans to be modified before execution.
 */
public interface PlanCustomizer {

  /**
   * Takes a reference to a plan, returning a reference to a modified plan derived from the original.
   * <p>
   * By default, it returns the plan unmodified.
   *
   * @param plan
   * @return
   */
  default Plan updatePlan(Plan plan) {
    return plan;
  }

  /**
   * Takes a reference to the uninstall plan, returning a reference to a modified plan derived from the original.
   * <p>
   * By default, it returns the plan unmodified.
   *
   * @param uninstallPlan
   * @return
   */
  default Plan updateUninstallPlan(Plan uninstallPlan) {
    return uninstallPlan;
  }
}
