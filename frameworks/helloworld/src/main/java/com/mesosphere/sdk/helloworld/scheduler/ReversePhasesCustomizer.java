package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.plan.*;

import java.util.Collections;

/**
 * This customizer illustrates an example of modifying a plan at runtime to execute the steps in deployment phases
 * in reverse order.
 */
public class ReversePhasesCustomizer implements PlanCustomizer {
    @Override
    public Plan updatePlan(Plan plan) {
        if (plan.getName().equals(Constants.DEPLOY_PLAN_NAME)) {
            plan.getChildren().forEach(phase -> Collections.reverse(phase.getChildren()));
        }
        return plan;
    }
}
