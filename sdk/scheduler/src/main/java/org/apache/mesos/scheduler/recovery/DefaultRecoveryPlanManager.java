package org.apache.mesos.scheduler.recovery;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.scheduler.ChainedObserver;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.strategy.RandomStrategy;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.FailureMonitor;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.state.StateStoreUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link DefaultRecoveryPlanManager} enables monitoring and management of recovery plan.
 * <p>
 * This is an implementation of {@code PlanManager} that performs task recovery using dynamically generated
 * {@code Plan}. {@link DefaultRecoveryPlanManager} tracks currently failed (permanent) and stopped (transient) tasks,
 * generates a new {@link DefaultRecoveryStep} for them and adds them to the recovery Plan, if not already added.
 */
public class DefaultRecoveryPlanManager extends ChainedObserver implements PlanManager {
    private static final String RECOVERY_ELEMENT_NAME = "recovery";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    protected volatile Plan plan;

    private final StateStore stateStore;
    private final RecoveryRequirementProvider recoveryReqProvider;
    private final FailureMonitor failureMonitor;
    private final LaunchConstrainer launchConstrainer;
    private final Object planLock = new Object();

    public DefaultRecoveryPlanManager(
            StateStore stateStore,
            RecoveryRequirementProvider recoveryReqProvider,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor) {
        this.stateStore = stateStore;
        this.recoveryReqProvider = recoveryReqProvider;
        this.failureMonitor = failureMonitor;
        this.launchConstrainer = launchConstrainer;
        plan = new DefaultPlan(RECOVERY_ELEMENT_NAME, Collections.emptyList());
    }

    @Override
    public Plan getPlan() {
        synchronized (planLock) {
            return plan;
        }
    }

    private void setPlan(Plan plan) {
        synchronized (planLock) {
            this.plan = plan;
            this.plan.subscribe(this);
            List<String> stepNames =  plan.getChildren().stream()
                    .flatMap(phase -> phase.getChildren().stream())
                    .map(step -> step.getName())
                    .collect(Collectors.toList());
            logger.info("Recovery plan set to: {}", stepNames);
        }
    }

    @Override
    public Collection<? extends Step> getCandidates(Collection<String> dirtyAssets) {
        synchronized (planLock) {
            updatePlan(dirtyAssets);
            return PlanUtils.getCandidates(getPlan(), dirtyAssets).stream()
                    .filter(step ->
                            launchConstrainer.canLaunch(((DefaultRecoveryStep) step).getRecoveryRequirement()))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Updates the recovery plan if necessary.
     * <p>
     * 1. Updates existing steps.
     * 2. If the needs recovery and doesn't yet have a step in the plan, removes any COMPLETED steps for this task
     * (at most one step for a given task can exist) and creates a new PENDING step.
     *
     * @param status task status
     */
    @Override
    public void update(Protos.TaskStatus status) {
        synchronized (planLock) {
            getPlan().update(status);
            notifyObservers();
        }
    }
    private void updatePlan(Collection<String> dirtyAssets) {
        logger.info("Dirty assets for recovery plan consideration: {}", dirtyAssets);

        synchronized (planLock) {
            // This list will not contain any Complete steps.
            List<Step> steps = createSteps(dirtyAssets);
            List<String> stepNames = steps.stream().map(step -> step.getName()).collect(Collectors.toList());
            logger.info("New recovery steps: {}", stepNames);

            List<Step> oldSteps = getPlan().getChildren().stream()
                    .flatMap(phase -> phase.getChildren().stream())
                    .filter(step -> !stepNames.contains(step.getName()))
                    .collect(Collectors.toList());
            logger.info("Old recovery steps: {}",
                    oldSteps.stream().map(step -> step.getName()).collect(Collectors.toList()));

            steps.addAll(oldSteps);
            setPlan(createPlan(steps));
        }
    }

    private Plan createPlan(List<Step> steps) {
        Phase phase = DefaultPhaseFactory.getPhase(RECOVERY_ELEMENT_NAME, steps, new RandomStrategy<>());
        return DefaultPlanFactory.getPlan(RECOVERY_ELEMENT_NAME, Arrays.asList(phase), new SerialStrategy<>());
    }

    List<Step> createSteps(Collection<String> dirtyAssets) {
        Collection<Protos.TaskInfo> taskInfos = StateStoreUtils.fetchTasksNeedingRecovery(stateStore);

        List<Step> steps = new ArrayList<>();
        for (Protos.TaskInfo taskInfo : taskInfos) {
            try {
                steps.addAll(createSteps(TaskUtils.unpackTaskInfo(taskInfo)));
            } catch (TaskException | InvalidRequirementException | InvalidProtocolBufferException e) {
                steps.add(
                        new DefaultStep(
                                taskInfo.getName(),
                                Optional.empty(),
                                Status.ERROR,
                                null,
                                Arrays.asList(ExceptionUtils.getStackTrace(e))));
            }
        }


        return steps;
    }

    private List<Step> createSteps(List<RecoveryRequirement> recoveryRequirements) {
        return recoveryRequirements.stream()
                .map(recoveryRequirement -> new DefaultRecoveryStep(
                        recoveryRequirement.getPodInstance().getName(),
                        Status.PENDING,
                        recoveryRequirement.getPodInstance(),
                        recoveryRequirement,
                        launchConstrainer))
                .collect(Collectors.toList());
    }

    private List<Step> createSteps(Protos.TaskInfo taskInfo)
            throws TaskException, InvalidRequirementException {
        final List<RecoveryRequirement> recoveryRequirements;

        if (FailureUtils.isLabeledAsFailed(taskInfo) || failureMonitor.hasFailed(taskInfo)) {
            recoveryRequirements = recoveryReqProvider.getPermanentRecoveryRequirements(Arrays.asList(taskInfo));
        } else {
            recoveryRequirements = recoveryReqProvider.getTransientRecoveryRequirements(Arrays.asList(taskInfo));
        }

        return recoveryRequirements.stream()
                .map(recoveryRequirement -> new DefaultRecoveryStep(
                        taskInfo.getName(),
                        Status.PENDING,
                        recoveryRequirement.getPodInstance(),
                        recoveryRequirement,
                        launchConstrainer))
                .collect(Collectors.toList());
    }

    @Override
    public Set<String> getDirtyAssets() {
        Set<String> dirtyAssets = new HashSet<>();
        if (plan != null) {
            dirtyAssets.addAll(plan.getChildren().stream()
                    .flatMap(phase -> phase.getChildren().stream())
                    .filter(step -> step.isInProgress())
                    .map(step -> step.getName())
                    .collect(Collectors.toSet()));
        }
        return dirtyAssets;
    }
}
