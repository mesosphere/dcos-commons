package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.*;
import org.apache.mesos.scheduler.*;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.strategy.RandomStrategy;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.FailureMonitor;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.InvalidTaskSpecificationException;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link DefaultRecoveryPlanManager} enables monitoring and management of recovery plan.
 *
 * This is an implementation of {@code PlanManager} that performs task recovery using dynamically generated
 * {@code Plan}. {@link DefaultRecoveryPlanManager} tracks currently failed (permanent) and stopped (transient) tasks,
 * generates a new {@link DefaultRecoveryBlock} for them and adds them to the recovery Plan, if not already added.
 */
public class DefaultRecoveryPlanManager extends ChainedObserver implements PlanManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRecoveryPlanManager.class);
    private static final String RECOVERY_ELEMENT_NAME = "recovery";

    protected volatile Plan plan;

    private final StateStore stateStore;
    private final RecoveryRequirementProvider offerReqProvider;
    private final FailureMonitor failureMonitor;
    private final LaunchConstrainer launchConstrainer;

    public DefaultRecoveryPlanManager(
            StateStore stateStore,
            RecoveryRequirementProvider offerReqProvider,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor) {
        this.stateStore = stateStore;
        this.offerReqProvider = offerReqProvider;
        this.failureMonitor = failureMonitor;
        this.launchConstrainer = launchConstrainer;
        updatePlan(Collections.emptyList());
    }

    @Override
    public Plan getPlan() {
        return plan;
    }

    @Override
    public Collection<? extends Block> getCandidates(Collection<String> dirtyAssets) {
        return PlanUtils.getCandidates(plan, dirtyAssets).stream()
                .filter(block -> launchConstrainer.canLaunch(((DefaultRecoveryBlock) block).getRecoveryRequirement()))
                .collect(Collectors.toList());
    }

    /**
     * Updates the recovery plan if necessary.
     *
     * 1. Updates existing blocks.
     * 2. If the needs recovery and doesn't yet have a block in the plan, removes any COMPLETED blocks for this task
     *    (at most one block for a given task can exist) and creates a new PENDING block.
     *
     * @param status task status
     */
    @Override
    public void update(Protos.TaskStatus status) {
        try {
            String taskName = TaskUtils.toTaskName(status.getTaskId());

            Optional<Protos.TaskInfo> taskInfo = stateStore.fetchTask(taskName);
            if (!taskInfo.isPresent()) {
                LOGGER.error("Failed to fetch TaskInfo for task (name={})", taskName);
                return;
            }

            // 1. Update the existing Plan.
            plan.update(status);

            boolean blockExists = this.plan.getChildren().stream()
                    .filter(phase -> phase.getChildren().stream()
                            .filter(block -> block.getName().equals(taskName)).findAny().isPresent())
                    .findAny().isPresent();

            if (!blockExists && TaskUtils.needsRecovery(status)) {
                Optional<Block> newBlock = createBlock(taskInfo.get());
                if (newBlock.isPresent()) {
                    List<Block> blocks = new ArrayList<>();

                    // 2. remove any COMPLETED blocks for this task.
                    for (Phase phase : this.plan.getChildren()) {
                        blocks.addAll(phase.getChildren().stream()
                                .filter(block -> !(block.isComplete() && block.getName().equals(taskName)))
                                .collect(Collectors.toList()));
                    }

                    // 3. Add new PENDING block.
                    blocks.add(newBlock.get());

                    updatePlan(blocks);
                }
            }
        } catch (TaskException ex) {
            LOGGER.error("Error updating status for task (id={})", status.getTaskId().toString(), ex);
        }
    }

    private void updatePlan(List<Block> blocks) {
        Phase phase = DefaultPhaseFactory.getPhase(RECOVERY_ELEMENT_NAME, blocks, new RandomStrategy<>());
        plan = DefaultPlanFactory.getPlan(RECOVERY_ELEMENT_NAME, Arrays.asList(phase), new SerialStrategy<>());
        plan.subscribe(this);
        this.notifyObservers();
    }

    private Optional<Block> createBlock(Protos.TaskInfo taskInfo) throws TaskException {
        try {
            final OfferRequirementProvider offerRequirementProvider = new DefaultOfferRequirementProvider();
            final TaskSpecification taskSpec = DefaultTaskSpecification.create(taskInfo);
            final List<RecoveryRequirement> recoveryRequirements;

            if (FailureUtils.isLabeledAsFailed(taskInfo) || failureMonitor.hasFailed(taskInfo)) {
                recoveryRequirements = offerReqProvider.getPermanentRecoveryRequirements(Arrays.asList(taskInfo));
            } else {
                recoveryRequirements = offerReqProvider.getTransientRecoveryRequirements(Arrays.asList(taskInfo));
            }

            return Optional.of(new DefaultRecoveryBlock(
                    taskSpec.getName(),
                    offerRequirementProvider.getExistingOfferRequirement(taskInfo, taskSpec),
                    Status.PENDING,
                    recoveryRequirements.get(0),
                    launchConstrainer));
        } catch (InvalidTaskSpecificationException | InvalidRequirementException ex) {
            LOGGER.error("Error creating recovery block.", ex);
            return Optional.empty();
        }
    }
}
