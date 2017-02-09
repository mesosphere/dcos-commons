package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanManagerFactory;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.FailureMonitor;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.StateStore;

import java.util.Collection;
import java.util.Optional;

/**
 * The HdfsRecoveryPlanManagerFactory generates {@link HdfsRecoveryPlanManager}s.
 */
public class HdfsRecoveryPlanManagerFactory implements RecoveryPlanManagerFactory {
    @Override
    public PlanManager create(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor,
            Collection<Plan> plans) {
        return new HdfsRecoveryPlanManager(
                stateStore,
                configStore,
                launchConstrainer,
                failureMonitor,
                getNameNodeReplacementPlan(plans));
    }

    private Plan getNameNodeReplacementPlan(Collection<Plan> plans) {
        String planName = "replace-nn";
        Optional<Plan> planOptional = plans.stream()
                .filter(plan -> plan.getName().equals(planName))
                .findFirst();

        if (planOptional.isPresent()) {
            return planOptional.get();
        } else {
            throw new RuntimeException("Failed to find plan: " + planName);
        }
    }
}
