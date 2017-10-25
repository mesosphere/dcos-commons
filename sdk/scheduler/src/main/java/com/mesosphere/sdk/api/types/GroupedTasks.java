package com.mesosphere.sdk.api.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.state.StateStore;

/**
 * Utility class for sorting/grouping {@link TaskInfo}s and/or their associated {@link TaskStatus}es into pods.
 */
public class GroupedTasks {
    private static final Logger LOGGER = LoggerFactory.getLogger(GroupedTasks.class);

    /**
     * Mapping of pod type => pod index => to tasks in that pod.
     */
    public final Map<String, Map<Integer, List<TaskInfoAndStatus>>> byPodTypeAndIndex = new TreeMap<>();

    /**
     * List of tasks for which the pod instance couldn't be determined. Should be empty in practice.
     */
    public final List<TaskInfoAndStatus> unknownPod = new ArrayList<>();

    /**
     * Returns a new instance which contains all the tasks/statuses that are currently present in the provided
     * {@link StateStore}.
     */
    public static GroupedTasks create(StateStore stateStore) {
        return new GroupedTasks(stateStore.fetchTasks(), stateStore.fetchStatuses());
    }

    public Optional<Collection<TaskInfoAndStatus>> getPodInstanceTasks(String podInstanceName) {
        for (Map.Entry<String, Map<Integer, List<TaskInfoAndStatus>>> pod : byPodTypeAndIndex.entrySet()) {
            for (Map.Entry<Integer, List<TaskInfoAndStatus>> podInstance : pod.getValue().entrySet()) {
                if (PodInstance.getName(pod.getKey(), podInstance.getKey()).equals(podInstanceName)) {
                    return Optional.of(podInstance.getValue());
                }
            }
        }
        return Optional.empty();
    }

    private GroupedTasks(Collection<TaskInfo> taskInfos, Collection<TaskStatus> taskStatuses) {
        Map<TaskID, TaskStatus> taskStatusesById = taskStatuses.stream()
                .collect(Collectors.toMap(status -> status.getTaskId(), Function.identity()));

        // map TaskInfos (and TaskStatuses if available) into pod instances:
        for (TaskInfo taskInfo : taskInfos) {
            TaskInfoAndStatus taskInfoAndStatus = TaskInfoAndStatus.create(
                    taskInfo, Optional.ofNullable(taskStatusesById.get(taskInfo.getTaskId())));
            TaskLabelReader labels = new TaskLabelReader(taskInfo);
            try {
                String podType = labels.getType();
                Map<Integer, List<TaskInfoAndStatus>> podInstances = byPodTypeAndIndex.get(podType);
                if (podInstances == null) {
                    podInstances = new TreeMap<>();
                    byPodTypeAndIndex.put(podType, podInstances);
                }

                int podIndex = labels.getIndex();
                List<TaskInfoAndStatus> tasks = podInstances.get(podIndex);
                if (tasks == null) {
                    tasks = new ArrayList<>();
                    podInstances.put(podIndex, tasks);
                }

                tasks.add(taskInfoAndStatus);
            } catch (Exception e) {
                LOGGER.warn(String.format("Failed to extract pod information from task %s", taskInfo.getName()), e);
                unknownPod.add(taskInfoAndStatus);
            }
        }

        // sort the tasks within each pod by the task names (for user convenience):
        for (Map<Integer, List<TaskInfoAndStatus>> podInstances : byPodTypeAndIndex.values()) {
            for (List<TaskInfoAndStatus> tasks : podInstances.values()) {
                tasks.sort(new Comparator<TaskInfoAndStatus>() {
                    @Override
                    public int compare(TaskInfoAndStatus a, TaskInfoAndStatus b) {
                        return a.getInfo().getName().compareTo(b.getInfo().getName());
                    }
                });
            }
        }
    }
}
