package com.mesosphere.sdk.cockroachdb.scheduler;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.ParallelStrategy;
import com.mesosphere.sdk.scheduler.recovery.DefaultRecoveryStep;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverrider;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.scheduler.recovery.constrain.UnconstrainedLaunchConstrainer;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
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
        Step inputJoinStep = inputPhase.getChildren().get(0);
        Step inputMetricStep = inputPhase.getChildren().get(1);

        logger.info("Input Phase: {}", inputPhase);
        logger.info("Input Join Step: {}", inputJoinStep);
        logger.info("Input Metric Step: {}", inputMetricStep);


        PodInstanceRequirement joinPodInstanceRequirement =
             PodInstanceRequirement.newBuilder(
                    inputJoinStep.start().get().getPodInstance(),
                    inputJoinStep.start().get().getTasksToLaunch()
                    )
            .recoveryType(RecoveryType.PERMANENT)
            .build();

        PodInstance podInstance = inputMetricStep.start().get().getPodInstance();
        PodSpec podSpec = podInstance.getPod();
        TaskSpec taskSpec = podSpec.getTasks().stream().filter(t -> t.getName().equals("metrics")).findFirst().get();
        PodSpec newPodSpec = DefaultPodSpec.newBuilder(podSpec).tasks(Arrays.asList(taskSpec)).build();
        PodInstance newPodInstance = new DefaultPodInstance(newPodSpec, 0);
        PodInstanceRequirement metricPodInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        newPodInstance, inputMetricStep.start().get().getTasksToLaunch())
                        .recoveryType(RecoveryType.PERMANENT)
                        .build();

        logger.info("joinPodInstanceRequirement PodInstance: {}", joinPodInstanceRequirement.getPodInstance());
        logger.info("joinPodInstanceRequirement TaskToLaunch: {}", joinPodInstanceRequirement.getTasksToLaunch());
        logger.info("metricPodInstanceRequirement PodInstance: {}", metricPodInstanceRequirement.getPodInstance());
        logger.info("metricPodInstanceRequirement TaskToLaunch: {}", metricPodInstanceRequirement.getTasksToLaunch());

        Step joinStep = new DefaultRecoveryStep(
                inputJoinStep.getName(),
                Status.PENDING,
                joinPodInstanceRequirement,
                new UnconstrainedLaunchConstrainer(),
                stateStore);

        Step metricStep = new DefaultRecoveryStep(
                inputMetricStep.getName(),
                Status.PENDING,
                metricPodInstanceRequirement,
                new UnconstrainedLaunchConstrainer(),
                stateStore);

        Phase phase = new DefaultPhase(
                RECOVERY_PHASE_NAME,
                Arrays.asList(joinStep, metricStep),
                new ParallelStrategy<>(),
                Collections.emptyList());

        logger.info("Output Phase: {}", phase);
        logger.info("Output Join Step: {}", joinStep);
        logger.info("Output Metric Step: {}", metricStep);

        return phase;
      }
}
