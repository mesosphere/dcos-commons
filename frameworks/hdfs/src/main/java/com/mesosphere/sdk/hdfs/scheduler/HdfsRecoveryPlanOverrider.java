package com.mesosphere.sdk.hdfs.scheduler;

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

import java.util.*;

/**
 * The HdfsRecoveryPlanManager handles failure scenarios unique to HDFS.  It falls back to the default recovery behavior
 * when appropriate.
 */
public class HdfsRecoveryPlanOverrider implements RecoveryPlanOverrider {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String NN_PHASE_NAME = "permanent-nn-failure-recovery";
    private final StateStore stateStore;
    private final ConfigStore<ServiceSpec> configStore;
    private final Plan replaceNameNodePlan;

    public HdfsRecoveryPlanOverrider(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Plan replaceNameNodePlan) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.replaceNameNodePlan = replaceNameNodePlan;
    }

    @Override
    public Optional<Phase> override(PodInstanceRequirement stoppedPod) {
        if (!stoppedPod.getPodInstance().getPod().getType().equals("name")
                || stoppedPod.getRecoveryType() != RecoveryType.PERMANENT) {
            logger.info("No overrides necessary. Pod is not a name node or it isn't a permanent failure.");
            return Optional.empty();
        }

        Phase nnPhase = null;
        switch (stoppedPod.getPodInstance().getIndex()) {
            case 0:
                logger.info("Returning replacement plan for namenode 0.");
                return Optional.of(getNNRecoveryPhase(replaceNameNodePlan, 0));
            case 1:
                logger.info("Returning replacement plan for namenode 1.");
                return Optional.of(getNNRecoveryPhase(replaceNameNodePlan, 1));
            default:
                logger.error(
                        "Encountered unexpected index: {}, falling back to default recovery plan manager.",
                        stoppedPod.getPodInstance().getIndex());
                return Optional.empty();
        }
    }

    private Phase getNNRecoveryPhase(Plan inputPlan, int index) {
        Phase inputPhase = inputPlan.getChildren().get(0);
        int offset = index * 2;

        // Bootstrap
        Step inputBootstrapStep = inputPhase.getChildren().get(offset + 0);
        PodInstanceRequirement bootstrapPodInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        inputBootstrapStep.start().get().getPodInstance(),
                        inputBootstrapStep.start().get().getTasksToLaunch())
                .recoveryType(RecoveryType.PERMANENT)
                .build();
        Step bootstrapStep =
                new DefaultRecoveryStep(
                        inputBootstrapStep.getName(),
                        Status.PENDING,
                        bootstrapPodInstanceRequirement,
                        new UnconstrainedLaunchConstrainer(),
                        stateStore);

        // NameNode
        Step inputNodeStep = inputPhase.getChildren().get(offset + 1);
        PodInstanceRequirement nameNodePpodInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        inputNodeStep.start().get().getPodInstance(),
                        inputNodeStep.start().get().getTasksToLaunch())
                .recoveryType(RecoveryType.TRANSIENT)
                .build();
        Step nodeStep =
                new DefaultRecoveryStep(
                        inputNodeStep.getName(),
                        Status.PENDING,
                        nameNodePpodInstanceRequirement,
                        new UnconstrainedLaunchConstrainer(),
                        stateStore);

        return new DefaultPhase(
                NN_PHASE_NAME,
                Arrays.asList(bootstrapStep, nodeStep),
                new SerialStrategy<>(),
                Collections.emptyList());
    }
}
