package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.OfferEvaluationComponentFactory;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The OfferEvaluator processes {@link Protos.Offer}s and produces {@link OfferRecommendation}s.
 * The determination of what {@link OfferRecommendation}s, if any should be made are made
 * in reference to {@link PodInstanceRequirement}s.
 */
public class OfferEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(OfferEvaluator.class);

    private final StateStore stateStore;
    private final String serviceName;
    private final UUID targetConfigId;
    private final SchedulerConfig schedulerConfig;
    private Capabilities capabilities;
    private OfferEvaluationComponentFactory offerEvaluationComponentFactory;

    public OfferEvaluator(
            StateStore stateStore,
            String serviceName,
            UUID targetConfigId,
            SchedulerConfig schedulerConfig,
            Capabilities capabilities) {
        this.stateStore = stateStore;
        this.serviceName = serviceName;
        this.targetConfigId = targetConfigId;
        this.schedulerConfig = schedulerConfig;
        this.capabilities = capabilities;
    }

    private OfferEvaluationComponentFactory getOfferEvaluationComponentFactory() {
        if (offerEvaluationComponentFactory == null) {
            offerEvaluationComponentFactory = new OfferEvaluationComponentFactory(
                    capabilities, serviceName, stateStore.fetchFrameworkId().get(), targetConfigId, schedulerConfig);
        }

        return offerEvaluationComponentFactory;
    }

    public List<OfferRecommendation> evaluate(
            PodInstanceRequirement podInstanceRequirement, List<Protos.Offer> offers) throws SpecVisitorException {
        Map<String, Protos.TaskInfo> allTasks = stateStore.fetchTasks().stream()
                .collect(Collectors.toMap(Protos.TaskInfo::getName, Function.identity()));
        List<Protos.TaskInfo> thisPodTasks =
                TaskUtils.getTaskNames(podInstanceRequirement.getPodInstance()).stream()
                        .map(taskName -> allTasks.get(taskName))
                        .filter(taskInfo -> taskInfo != null)
                        .collect(Collectors.toList());
        logger.info("Pod: {}, taskInfos for evaluation.", podInstanceRequirement.getPodInstance().getName());
        thisPodTasks.forEach(info -> logger.info(TextFormat.shortDebugString(info)));

        List<Protos.TaskInfo> runningTasks = thisPodTasks.stream()
                .filter(task -> {
                    Optional<Protos.TaskStatus> status = stateStore.fetchStatus(task.getName());

                    return status.isPresent() && status.get().getState().equals(Protos.TaskState.TASK_RUNNING);
                })
                .collect(Collectors.toList());
        Map<TaskSpec, GoalStateOverride> goalStateOverrides = podInstanceRequirement.getPodInstance().getPod()
                .getTasks().stream()
                .filter(t -> podInstanceRequirement.getTasksToLaunch().contains(t.getName()))
                .collect(Collectors.toMap(
                        Function.identity(),
                        t -> stateStore.fetchGoalOverrideStatus(
                                TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), t)).target));

        OfferEvaluationComponentFactory offerEvaluationComponentFactory = getOfferEvaluationComponentFactory();
        for (int i = 0; i < offers.size(); ++i) {
            Protos.Offer offer = offers.get(i);
            MesosResourcePool resourcePool = new MesosResourcePool(
                    offer,
                    getRole(podInstanceRequirement.getPodInstance().getPod()));

            SpecVisitor<EvaluationOutcome> evaluationVisitor = offerEvaluationComponentFactory.getEvaluationVisitor(
                    resourcePool, thisPodTasks, runningTasks, allTasks.values(), goalStateOverrides);

            podInstanceRequirement.accept(evaluationVisitor);
            Collection<EvaluationOutcome> outcomes = evaluationVisitor.getResult();

            int failedOutcomeCount = 0;
            StringBuilder outcomeDetails = new StringBuilder();
            for (EvaluationOutcome outcome : outcomes) {
                if (!outcome.isPassing()) {
                    ++failedOutcomeCount;
                }
                logOutcome(outcomeDetails, outcome, "");
            }
            if (outcomeDetails.length() != 0) {
                // trim extra trailing newline:
                outcomeDetails.deleteCharAt(outcomeDetails.length() - 1);
            }

            if (failedOutcomeCount != 0) {
                logger.info("Offer {}, {}: failed {} of {} evaluation stages:\n{}",
                        i + 1,
                        offer.getId().getValue(),
                        failedOutcomeCount,
                        outcomes.size(),
                        outcomeDetails.toString());
            } else {
                List<OfferRecommendation> recommendations = outcomes.stream()
                        .map(outcome -> outcome.getOfferRecommendations())
                        .flatMap(xs -> xs.stream())
                        .collect(Collectors.toList());
                logger.info("Offer {}: passed all {} evaluation stages, returning {} recommendations:\n{}",
                        i + 1,
                        outcomes.size(),
                        recommendations.size(),
                        outcomeDetails.toString());

                return recommendations;
            }
        }

        return Collections.emptyList();
    }

    private static void logOutcome(StringBuilder stringBuilder, EvaluationOutcome outcome, String indent) {
        stringBuilder.append(String.format("  %s%s%n", indent, outcome.toString()));
        for (EvaluationOutcome child : outcome.getChildren()) {
            logOutcome(stringBuilder, child, indent + "  ");
        }
    }

    @VisibleForTesting
    UUID getTargetConfig(PodInstanceRequirement podInstanceRequirement, Collection<Protos.TaskInfo> taskInfos) {
        if (podInstanceRequirement.getRecoveryType().equals(RecoveryType.NONE) || taskInfos.isEmpty()) {
            return targetConfigId;
        } else {
            // 1. Recovery always only handles tasks with a goal state of RUNNING
            // 2. All tasks in a pod should be launched with the same configuration
            // Therefore it is correct to take the target configuration of one task as being
            // representative of the whole of the pod. If tasks in the same pod with a goal
            // state of RUNNING had different target configurations this should be rectified
            // in any case, so it is doubly proper to choose a single target configuration as
            // representative of the whole pod's target configuration.

            Protos.TaskInfo taskInfo = taskInfos.stream().findFirst().get();
            try {
                return new TaskLabelReader(taskInfo).getTargetConfiguration();
            } catch (TaskException e) {
                logger.error(String.format(
                        "Falling back to current target configuration '%s'. " +
                                "Failed to determine target configuration for task: %s",
                                targetConfigId, TextFormat.shortDebugString(taskInfo)), e);
                return targetConfigId;
            }
        }
    }

    private static Optional<String> getRole(PodSpec podSpec) {
        return podSpec.getTasks().stream()
                .map(TaskSpec::getResourceSet)
                .flatMap(resourceSet -> resourceSet.getResources().stream())
                .map(ResourceSpec::getRole)
                .findFirst();
    }
}
