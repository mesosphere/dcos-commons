package com.mesosphere.sdk.offer.evaluate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;

class ExistingTaskEvaluationPipeline {

    private static final Logger logger = LoggingUtils.getLogger(ExistingTaskEvaluationPipeline.class);
    private final PodInstanceRequirement podInstanceRequirement;
    private final List<TaskSpec> taskSpecsToReserveAndLaunch = new ArrayList<>();
    private final List<TaskSpec> taskSpecsToReserve = new ArrayList<>();
    private final List<TaskSpec> taskSpecsToUpdateInfo = new ArrayList<>();

    ExistingTaskEvaluationPipeline(StateStore stateStore, PodInstanceRequirement podInstanceRequirement) {
        this.podInstanceRequirement = podInstanceRequirement;

        for (TaskSpec taskSpec : podInstanceRequirement.getPodInstance().getPod().getTasks()) {
            // Case 1: The task is explicitly being launched as part of this evaluation. Include it in the tasks to be
            // launched following any reservation updates.

            if (podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName())) {
                // It is explicitly requested that this task be launched as a part of this evaluation, with any changes
                // applied automatically as part of the evaluation/relaunch.
                taskSpecsToReserveAndLaunch.add(taskSpec);
                continue;
            }

            // Case 2: The task is a sidecar task. Update its assigned configuration ID in ZK to ensure that it doesn't
            // drift away from the rest of the pod, and also update its reservations if it's not currently running.

            // NOTE: If the sidecar task appears to be CURRENTLY running, we will avoid updating its reservations now,
            // because we cannot update the reservations of a running task. Instead, we will just update its config ID,
            // which will get picked up the next time that the sidecar task is run. At that point, we will automatically
            // detect and apply any relevant changes to the task and its reservations.

            switch (taskSpec.getGoal()) {
            case FINISH:
                // fall through
            case ONCE: {
                Optional<Protos.TaskStatus> taskStatus = stateStore.fetchStatus(
                        TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec.getName()));
                if (taskStatus.isPresent() && !TaskUtils.isTerminal(taskStatus.get())) {
                    // The sidecar task is currently running. We cannot update its reservations until it has terminated.
                    // For now, just update its assigned config ID, and any changes to the task will take effect
                    // automatically the next time it's triggered. This isn't perfect, because we'd ideally like to
                    // update its reservations right now, but it's the best we can do.
                    taskSpecsToUpdateInfo.add(taskSpec);
                } else {
                    // The sidecar task is not currently running, so we are free to update its configuration now.
                    taskSpecsToReserve.add(taskSpec);
                }
                break;
            }
            case RUNNING:
                // This is not a sidecar task. It should only be updated when explicitly requested by the deploy plan,
                // via getTasksToLaunch() above.
                break;
            case UNKNOWN:
                // fall through
            default:
                throw new IllegalStateException(String.format("Unsupported goal state for task %s: %s",
                        TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec.getName()),
                        taskSpec.getGoal()));
            }
        }
    }

    /**
     * Finds and returns a random resource that's involved a launch operation for the pod.
     *
     * This can be used to determine roles/principals for the operation.
     */
    ResourceSpec getRandomLaunchableResourceSpec() {
        // Any operation should involve launching at least one task.
        return taskSpecsToReserveAndLaunch.stream()
                .filter(ts -> !ts.getResourceSet().getResources().isEmpty())
                .map(ts -> ts.getResourceSet().getResources().iterator().next())
                .findAny()
                .get();
    }

    /**
     * Returns the evaluation stages needed to relaunch a task. This may optionally include any autodetected changes
     * to the task's reserved resources.
     */
    Collection<OfferEvaluationStage> getTaskStages(
            String serviceName,
            Optional<String> resourceNamespace,
            Map<String, Protos.TaskInfo> podTasks) {
        Collection<OfferEvaluationStage> evaluationStages = new ArrayList<>();
        for (TaskSpec taskSpec : taskSpecsToReserveAndLaunch) {
            evaluationStages.addAll(getTaskStages(serviceName, resourceNamespace, podTasks, taskSpec, true));
        }
        for (TaskSpec taskSpec : taskSpecsToReserve) {
            evaluationStages.addAll(getTaskStages(serviceName, resourceNamespace, podTasks, taskSpec, false));
        }
        for (TaskSpec taskSpec : taskSpecsToUpdateInfo) {
            evaluationStages.add(new LaunchEvaluationStage(serviceName, taskSpec.getName(), false));
        }
        return evaluationStages;
    }

    private Collection<OfferEvaluationStage> getTaskStages(
            String serviceName,
            Optional<String> resourceNamespace,
            Map<String, Protos.TaskInfo> podTasks,
            TaskSpec taskSpec,
            boolean shouldLaunch) {
        Optional<Protos.TaskInfo> taskInfo =
                getTaskInfoSharingResourceSet(podInstanceRequirement.getPodInstance(), taskSpec, podTasks);
        if (!taskInfo.isPresent()) {
            // Given that we're reevaluating an existing pod, there should be a TaskInfo for it...
            logger.error("Failed to fetch existing task {}, unable to generate evaluation pipeline",
                    TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec.getName()));
            return Collections.emptyList();
        }

        Collection<OfferEvaluationStage> evaluationStages = new ArrayList<>();
        TaskResourceMapper taskResourceMapper =
                new TaskResourceMapper(taskSpec, taskInfo.get(), resourceNamespace);
        taskResourceMapper.getOrphanedResources()
                .forEach(resource -> evaluationStages.add(new UnreserveEvaluationStage(resource)));

        evaluationStages.addAll(taskResourceMapper.getEvaluationStages());
        evaluationStages.add(
                new LaunchEvaluationStage(serviceName, taskSpec.getName(), shouldLaunch));
        return evaluationStages;
    }

    static Optional<Protos.TaskInfo> getTaskInfoSharingResourceSet(
            PodInstance podInstance,
            TaskSpec taskSpec,
            Map<String, Protos.TaskInfo> podTasks) {

        // See if there's already a TaskInfo for this task.
        String taskInfoName = TaskSpec.getInstanceName(podInstance, taskSpec.getName());
        Protos.TaskInfo taskInfo = podTasks.get(taskInfoName);
        if (taskInfo != null) {
            return Optional.of(taskInfo);
        }

        // Fall back to searching for any task in the pod with the same resource set as this task.
        String resourceSetId = taskSpec.getResourceSet().getId();
        return podInstance.getPod().getTasks().stream()
                .filter(ts -> ts.getResourceSet().getId().equals(resourceSetId))
                .map(ts -> podTasks.get(TaskSpec.getInstanceName(podInstance, ts.getName())))
                .filter(podTaskInfo -> podTaskInfo != null)
                .findAny();
    }
}