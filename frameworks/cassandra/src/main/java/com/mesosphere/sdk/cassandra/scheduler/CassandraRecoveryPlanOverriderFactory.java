package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverrider;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverriderFactory;
import com.mesosphere.sdk.state.StateStore;

import java.util.Collection;
import java.util.Optional;

/**
 * This class generates {@link CassandraRecoveryPlanOverrider}s.
 */
public class CassandraRecoveryPlanOverriderFactory implements RecoveryPlanOverriderFactory {
    private static final String REPLACE_PLAN_NAME = "replace";

    @Override
    public RecoveryPlanOverrider create(StateStore stateStore, Collection<Plan> plans) {
        return new CassandraRecoveryPlanOverrider(stateStore, getNodeReplacementPlan(plans));
    }

    private Plan getNodeReplacementPlan(Collection<Plan> plans) {
        Optional<Plan> planOptional = plans.stream()
                .filter(plan -> plan.getName().equals(REPLACE_PLAN_NAME))
                .findFirst();

        if (planOptional.isPresent()) {
            return planOptional.get();
        } else {
            throw new RuntimeException("Failed to find plan: " + REPLACE_PLAN_NAME);
        }
    }
}
