package com.mesosphere.sdk.cockroachdb.scheduler;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.recovery.DefaultRecoveryStep;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverrider;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.scheduler.recovery.constrain.UnconstrainedLaunchConstrainer;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Optional;

/**
 * The CockroachdbRecoveryPlanManager handles failure scenarios unique to Cockroachdb. 
 * It falls back to the default recovery
 * behavior when appropriate.
 */
public class CockroachdbRecoveryPlanOverrider implements RecoveryPlanOverrider {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String RECOVERY_PHASE_NAME = "permanent-node-failure-recovery";
    private final StateStore stateStore;
    private final ConfigStore<ServiceSpec> configStore;
    private final Plan replaceNodePlan;

    public CockroachdbRecoveryPlanOverrider(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Plan replaceNodePlan) {
            this.stateStore = stateStore;
            this.configStore = configStore;
            this.replaceNodePlan = replaceNodePlan;
            }

    @Override
    public Optional<Phase> override(PodInstanceRequirement stoppedPod) {
        if (stoppedPod.getPodInstance().getIndex() != 0) {
            logger.info("No overrides necessary. Pod is not cockroachdb-0.");
            return Optional.empty();
        }

        if (stoppedPod.getRecoveryType() != RecoveryType.PERMANENT) {
            logger.info("No overrides necessary, RecoveryType is {}.", stoppedPod.getRecoveryType());
            return Optional.empty();
        }

        stateStore.clearTask("cockroachdb-0-node-init");

        logger.info("Returning replace plan for cockroachdb-0");
        return Optional.ofNullable(getNodeRecoveryPhase(replaceNodePlan));
    }

    private Phase getNodeRecoveryPhase(Plan inputPlan) {
        Phase inputPhase = inputPlan.getChildren().get(0);
        Step inputStep = inputPhase.getChildren().get(0);

        logger.info("Input Phase: {}", inputPhase);
        logger.info("Input Step: {}", inputStep);

        PodInstanceRequirement joinPodInstanceRequirement =
             PodInstanceRequirement.newBuilder(
                    inputStep.start().get().getPodInstance(),
                    inputStep.start().get().getTasksToLaunch()
                    )
            .recoveryType(RecoveryType.PERMANENT)
            .build();

        logger.info("PodInstanceRequirement: {}", joinPodInstanceRequirement);

        Step joinStep = new DefaultRecoveryStep(
                inputStep.getName(),
                Status.PENDING,
                joinPodInstanceRequirement,
                new UnconstrainedLaunchConstrainer(),
                stateStore);

        logger.info("Join Step: {}", joinStep);

        Phase phase = new DefaultPhase(
                RECOVERY_PHASE_NAME,
                Collections.singletonList(joinStep),
                new SerialStrategy<>(),
                Collections.emptyList());

        logger.info("Replacement phase: {}", phase);

        return phase;
      }
}
