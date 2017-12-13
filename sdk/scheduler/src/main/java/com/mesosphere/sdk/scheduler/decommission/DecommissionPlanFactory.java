package com.mesosphere.sdk.scheduler.decommission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.scheduler.plan.DefaultPhase;
import com.mesosphere.sdk.scheduler.plan.DefaultPlan;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.uninstall.ResourceCleanupStep;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;

/**
 * Handles creation of the decommission plan, used for removing nodes from a service.
 *
 * The plan is structured as a series of phases, one per pod to be decommissioned. Each pod will be decommissioned as
 * follows:
 *
 * <ol><li>Tasks in the pod are set to a DECOMMISSIONED+IN_PROGRESS state, from DECOMMISSIONED+PENDING which was set
 * when the Plan was initialized.</li>
 * <li>Kill is issued for all tasks in the pod.</li>
 * <li>As resources for tasks within the pod are offered, the DECOMMISSIONED+IN_PROGRESS state results in those
 * resources being unreserved and removed from the {@code TaskInfo}.</li>
 * <li>Once the resources are all cleared, the task is deleted from the {@link StateStore}.</li></ol>
 *
 * Note that this is different from uninstall behavior in a couple ways, resulting in different handling from uninstall:
 *
 * <ul><li>The decommission operation can be cancelled by incrementing pod count, whereas uninstall cannot be cancelled
 * once it's started. We remove resources from the {@code TaskInfo}s instead of marking them with tombstones to reduce
 * the likelihood of a partial decommission causing problems.</li>
 * <li>The decommission operation occurs serially in a predictable order, whereas uninstall attempts to unreserve all
 * known resources in parallel. We enforce this by having the decommission plan set the DECOMMISSIONED+IN_PROGRESS bit
 * one pod at a time, and only unreserve pods that are marked as DECOMMISSIONED+IN_PROGRESS.</li></ul>
 */
public class DecommissionPlanFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DecommissionPlanFactory.class);

    /**
     * The status for a task whose resources should be unreserved because its resources are currently being
     * decommissioned.
     */
    public static final GoalStateOverride.Status DECOMMISSIONING_STATUS =
            GoalStateOverride.DECOMMISSIONED.newStatus(GoalStateOverride.Progress.IN_PROGRESS);

    private final PlanInfo planInfo;

    public DecommissionPlanFactory(ServiceSpec serviceSpec, StateStore stateStore, TaskKiller taskKiller) {
        this.planInfo = buildPlanInfo(serviceSpec, stateStore, taskKiller);
    }

    /**
     * Returns a {@link Plan} for decommissioning tasks, or an empty {@link Optional} if nothing needs to be
     * decommissioned.
     */
    public Optional<Plan> getPlan() {
        return planInfo.plan;
    }

    /**
     * Returns all {@link ResourceCleanupStep}s associated with the decommission plan, or an empty list if no steps are
     * applicable.
     */
    public Collection<Step> getResourceSteps() {
        return planInfo.resourceSteps;
    }

    private static class PlanInfo {
        private final Optional<Plan> plan;
        private final Collection<Step> resourceSteps;

        private PlanInfo(Optional<Plan> plan, Collection<Step> resourceSteps) {
            this.plan = plan;
            this.resourceSteps = resourceSteps;
        }
    }

    /**
     * Returns a {@link Plan} for decommissioning tasks, or an empty {@link Optional} if no decommission is necessary.
     */
    private static PlanInfo buildPlanInfo(ServiceSpec serviceSpec, StateStore stateStore, TaskKiller taskKiller) {
        // Determine which tasks should be decommissioned (and which shouldn't)
        Collection<Protos.TaskInfo> allTasks = stateStore.fetchTasks();
        SortedMap<PodKey, Collection<Protos.TaskInfo>> podsToDecommission =
                getPodsToDecommission(serviceSpec, allTasks);
        Set<String> tasksToDecommission = new HashSet<>();
        for (Collection<Protos.TaskInfo> podTasks : podsToDecommission.values()) {
            tasksToDecommission.addAll(podTasks.stream().map(task -> task.getName()).collect(Collectors.toSet()));
        }

        // Update state store override bits to reflect current decommission state (or lack thereof).
        // This will be visible to the operator via pod status, showing any affected pods as DECOMMISSIONING.
        for (Protos.TaskInfo task : allTasks) {
            GoalStateOverride.Status taskOverride = stateStore.fetchGoalOverrideStatus(task.getName());
            if (tasksToDecommission.contains(task.getName())) {
                if (taskOverride.target != GoalStateOverride.DECOMMISSIONED) {
                    // Set decommission bit: Task to be decommissioned hasn't been marked as such yet
                    LOGGER.info("Marking '{}' as pending decommission", task.getName());
                    stateStore.storeGoalOverrideStatus(task.getName(),
                            GoalStateOverride.DECOMMISSIONED.newStatus(GoalStateOverride.Progress.PENDING));
                }
            } else {
                if (taskOverride.target == GoalStateOverride.DECOMMISSIONED) {
                    // Clear decommission bit: Task isn't targeted for decommissioning anymore
                    // This can happen if a prior decommission operation was aborted
                    LOGGER.info("Clearing prior '{}' decommission state", task.getName());
                    stateStore.storeGoalOverrideStatus(task.getName(), GoalStateOverride.Status.INACTIVE);
                }
            }
        }

        if (podsToDecommission.isEmpty()) {
            return new PlanInfo(Optional.empty(), Collections.emptyList());
        }

        Collection<Step> resourceSteps = new ArrayList<>();
        List<Phase> phases = new ArrayList<>();
        // Each pod to be decommissioned gets its own phase in the decommission plan:
        for (Map.Entry<PodKey, Collection<Protos.TaskInfo>> entry : podsToDecommission.entrySet()) {
            List<Step> steps = new ArrayList<>();

            // 1. Kill pod's tasks
            steps.addAll(entry.getValue().stream()
                    .map(task -> new TriggerDecommissionStep(stateStore, taskKiller, task))
                    .collect(Collectors.toList()));

            // 2. Unreserve pod's resources
            // Note: Even though this step is in a serial phase, in practice resource cleanup should be done in
            // parallel, as all the tasks had been flagged for decommissioning via TriggerDecommissionStep.
            Collection<ResourceCleanupStep> resourceStepsForPod =
                    ResourceUtils.getResourceIds(ResourceUtils.getAllResources(entry.getValue())).stream()
                            .map(resourceId -> new ResourceCleanupStep(resourceId, Status.PENDING))
                            .collect(Collectors.toList());
            resourceSteps.addAll(resourceStepsForPod);
            steps.addAll(resourceStepsForPod);

            // 3. Delete pod's tasks from ZK
            // Note: As a side effect, this will also clear the override status.
            steps.addAll(entry.getValue().stream()
                    .map(task -> new EraseTaskStateStep(stateStore, task.getName()))
                    .collect(Collectors.toList()));

            phases.add(new DefaultPhase(
                    entry.getKey().getPodName(), steps, new SerialStrategy<>(), Collections.emptyList()));
        }

        return new PlanInfo(Optional.of(new DefaultPlan(Constants.DECOMMISSION_PLAN_NAME, phases)), resourceSteps);
    }

    /**
     * A utility class for ordering a {@link SortedMap} of pods to be decommissioned.
     */
    @VisibleForTesting
    static class PodKey implements Comparable<PodKey> {
        private final int podTypeIndex;
        private final String podType;
        private final int podIndex;

        /**
         * Constructor for {@link PodKey}.
         *
         * @param podType The type of pod. See: {@link PodSpec#getType()}
         * @param podIndex The index of the pod instance.  See: {@link PodInstance#getIndex()}.
         * @param orderedPodTypes the list of known pod types in order of priority (reverse of ServiceSpec ordering)
         */
        PodKey(String podType, int podIndex, List<String> orderedPodTypes) throws TaskException {
            this.podType = podType;
            // If the pod is unknown, it gets a -1, placing it at higher priority than listed pods:
            this.podTypeIndex = orderedPodTypes.indexOf(podType);
            this.podIndex = podIndex;
        }

        /**
         * Orders the pod according to the following rules:
         * <ol><li>Pod types at the bottom of the ServiceSpec (lower podTypeIndex) have priority over ones at the top.
         * Effectively the reverse of the default deployment ordering based on ServiceSpec ordering.</li>
         * <li>Pod types missing from the ServiceSpec have priority over listed pod types. If multiple of these unknown
         * pod types are involved, they are decommissioned in alphabetical order ("unknownA" before "unknownB").</li>
         * <li>Within a given pod type, pods with higher index are given priority ("pod-3" before "pod-2")</li></ol>
         */
        @Override
        public int compareTo(PodKey other) {
            if (podTypeIndex != other.podTypeIndex) {
                // Order pods by ServiceSpec ordering (unlisted in ServiceSpec => -1 index => higher priority)
                // (pod2 before pod1, and unknown before pod2)
                return podTypeIndex - other.podTypeIndex;
            } else if (podTypeIndex == -1 && !podType.equals(other.podType)) {
                // The pod types are both unlisted, but are not equal to each other. Just order them alphabetically.
                // (unknownA before unknownB)
                return podType.compareTo(other.podType);
            }
            // The pod types are indentical. Order them according to their index. (pod-3 before pod-2)
            return other.podIndex - podIndex;
        }

        public String getPodName() {
            return PodInstance.getName(podType, podIndex);
        }

        @Override
        public String toString() {
            return getPodName();
        }

        @Override
        public boolean equals(Object o) {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }
    }

    /**
     * Returns a mapping of pods to be decommissioned with affected tasks within those pods. The returned mapping will
     * be in the order that the pods should be decommissioned.
     */
    @VisibleForTesting
    static SortedMap<PodKey, Collection<Protos.TaskInfo>> getPodsToDecommission(
            ServiceSpec serviceSpec, Collection<Protos.TaskInfo> tasks) {
        // If multiple pod types are being decommissioned, they should be decommissioned in the reverse of the order
        // that they're declared in the ServiceSpec (opposite direction of default deployment)
        List<String> orderedPodTypes =
                serviceSpec.getPods().stream().map(PodSpec::getType).collect(Collectors.toList());
        Collections.reverse(orderedPodTypes);

        Map<String, Integer> expectedPodCounts =
                serviceSpec.getPods().stream().collect(Collectors.toMap(PodSpec::getType, PodSpec::getCount));
        LOGGER.info("Expected pod counts: {}", expectedPodCounts);
        SortedMap<PodKey, Collection<Protos.TaskInfo>> podsToDecommission = new TreeMap<>();
        for (Protos.TaskInfo task : tasks) {
            final PodKey podKey;
            try {
                TaskLabelReader labelReader = new TaskLabelReader(task);
                podKey = new PodKey(labelReader.getType(), labelReader.getIndex(), orderedPodTypes);
            } catch (TaskException e) {
                LOGGER.error(String.format(
                        "Failed to retrieve task metadata. Omitting task from decommission: %s", task.getName()), e);
                continue;
            }

            Integer expectedPodCount = expectedPodCounts.get(podKey.podType);
            if (expectedPodCount == null) {
                LOGGER.info("Scheduling '{}' for decommission: '{}' is not present in service spec: {}",
                        task.getName(), podKey.podType, expectedPodCounts.keySet());
            } else if (podKey.podIndex >= expectedPodCount) {
                LOGGER.info("Scheduling '{}' for decommission: '{}' exceeds desired pod count {}",
                        task.getName(), podKey.getPodName(), expectedPodCount);
            } else {
                // Do nothing
                continue;
            }
            Collection<Protos.TaskInfo> podTasks = podsToDecommission.get(podKey);
            if (podTasks == null) {
                podTasks = new ArrayList<>();
                podsToDecommission.put(podKey, podTasks);
            }
            podTasks.add(task);
        }
        LOGGER.info("Pods scheduled for decommission: {}", podsToDecommission.keySet());
        return podsToDecommission;
    }
}
