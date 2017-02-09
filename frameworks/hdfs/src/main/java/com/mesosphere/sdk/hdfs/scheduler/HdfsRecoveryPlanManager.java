package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.ParallelStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.recovery.DefaultRecoveryPlanManager;
import com.mesosphere.sdk.scheduler.recovery.DefaultRecoveryStep;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.FailureMonitor;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The HdfsRecoveryPlanManager handles failure scenarios unique to HDFS.  It falls back to the default recovery behavior
 * when appropriate.
 */
public class HdfsRecoveryPlanManager extends DefaultRecoveryPlanManager {
    private static final String NN_PHASE_NAME = "permanent-nn-failure-recovery";
    private final Plan replaceNameNodePlan;
    private Phase nameNodeRecoveryPhase = new DefaultPhase(
            NN_PHASE_NAME,
            Collections.emptyList(),
            new SerialStrategy<>(),
            Collections.emptyList());

    public HdfsRecoveryPlanManager(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor,
            Plan replaceNameNodePlan) {
        super(stateStore, configStore, launchConstrainer, failureMonitor);
        this.replaceNameNodePlan = replaceNameNodePlan;
    }

    @Override
    public Plan getPlan() {
        List<Phase> phases = new ArrayList<>();
        List<String> nnStepNames = nameNodeRecoveryPhase.getChildren().stream()
                .map(step -> step.getName())
                .collect(Collectors.toList());

        // Filter out steps handled by name node recovery from the default plan.
        for (Phase phase : super.getPlan().getChildren()) {
            List<Step> steps = phase.getChildren().stream()
                    .filter(step -> !nnStepNames.contains(step.getName()))
                    .collect(Collectors.toList());
            phases.add(
                    new DefaultPhase(
                            phase.getName(),
                            steps,
                            new SerialStrategy<>(),
                            Collections.emptyList()));
        }

        if (!nameNodeRecoveryPhase.getChildren().isEmpty()) {
            phases.add(nameNodeRecoveryPhase);
        }

        return new DefaultPlan(
                RECOVERY_ELEMENT_NAME,
                phases,
                new ParallelStrategy<>(),
                Collections.emptyList());
    }

    @Override
    public Collection<? extends Step> getCandidates(Collection<String> dirtyAssets) {
        /**
         * Always allow the default recovery manager to update its plan.  Otherwise, later
         * calls to {@link #getPlan()} will not contain the correct default recovery plan.
         */
        updatePlan(dirtyAssets);

        Map<PodInstance, List<Protos.TaskInfo>> failedNameNodes;
        try {
            failedNameNodes = TaskUtils.getPodMap(
                    configStore,
                    StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore));
        } catch (TaskException e) {
            logger.error("Failed to get tasks needing recovery, falling back to default recovery plan manager.", e);
            return super.getCandidates(dirtyAssets);
        }

        failedNameNodes = failedNameNodes.entrySet().stream()
                .filter(podInstanceListEntry -> podInstanceListEntry.getKey().getPod().getType().equals("name"))
                .collect(Collectors.toMap(
                        podInstanceListEntry -> podInstanceListEntry.getKey(),
                        podInstanceListEntry -> podInstanceListEntry.getValue()));

        Predicate<Protos.TaskInfo> isPodPermanentlyFailed = t -> (
                FailureUtils.isLabeledAsFailed(t) || failureMonitor.hasFailed(t));

        for (Map.Entry<PodInstance, List<Protos.TaskInfo>> failedPod : failedNameNodes.entrySet()) {
            PodInstance podInstance = failedPod.getKey();
            List<Protos.TaskInfo> failedTasks = failedPod.getValue();
            if (failedTasks.stream().allMatch(isPodPermanentlyFailed) && nameNodeRecoveryPhase.isComplete()) {
                switch (podInstance.getIndex()) {
                    case 0:
                        logger.info("Returning replacement plan for namenode 0.");
                        setNameNodePhase(initNNRecoveryPhase(replaceNameNodePlan, 0));
                        break;
                    case 1:
                        logger.info("Returning replacement plan for namenode 1.");
                        setNameNodePhase(initNNRecoveryPhase(replaceNameNodePlan, 1));
                        break;
                    default:
                        logger.error(
                                "Encountered unexpected index: {}, falling back to default recovery plan manager.",
                                podInstance.getIndex());
                }
            }
        }

        return PlanUtils.getCandidates(getPlan(), dirtyAssets).stream()
                .filter(step ->
                        launchConstrainer.canLaunch(((DefaultRecoveryStep) step).getRecoveryType()))
                .collect(Collectors.toList());
    }

    private void setNameNodePhase(Phase phase) {
        this.nameNodeRecoveryPhase = phase;
    }

    private Phase initNNRecoveryPhase(Plan inputPlan, int index) {
        Phase inputPhase = inputPlan.getChildren().get(0);
        int offset = index * 2;
        Step inputBootstrapStep = inputPhase.getChildren().get(offset + 0);
        Step bootstrapStep =
                new DefaultRecoveryStep(
                        inputBootstrapStep.getName(),
                        Status.PENDING,
                        inputBootstrapStep.start().get().getPodInstance(),
                        inputBootstrapStep.start().get().getTasksToLaunch(),
                        RecoveryType.PERMANENT,
                        launchConstrainer,
                        stateStore);

        Step inputNodeStep = inputPhase.getChildren().get(offset + 1);
        Step nodeStep =
                new DefaultRecoveryStep(
                        inputNodeStep.getName(),
                        Status.PENDING,
                        inputNodeStep.start().get().getPodInstance(),
                        inputNodeStep.start().get().getTasksToLaunch(),
                        RecoveryType.TRANSIENT,
                        launchConstrainer,
                        stateStore);

        return new DefaultPhase(
                NN_PHASE_NAME,
                Arrays.asList(bootstrapStep, nodeStep),
                new SerialStrategy<>(),
                Collections.emptyList());
    }
}
