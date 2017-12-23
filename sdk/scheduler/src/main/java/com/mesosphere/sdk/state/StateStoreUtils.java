package com.mesosphere.sdk.state;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.storage.StorageError.Reason;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utilities for implementations and users of {@link StateStore}.
 */
public class StateStoreUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateStoreUtils.class);
    private static final String UNINSTALLING_PROPERTY_KEY = "uninstalling";
    private static final String LAST_COMPLETED_UPDATE_TYPE_KEY = "last-completed-update-type";
    private static final String PROPERTY_TASK_INFO_SUFFIX = ":task-status";
    private static final byte[] DEPLOYMENT_TYPE = "DEPLOY".getBytes(StandardCharsets.UTF_8);

    private StateStoreUtils() {
        // do not instantiate
    }

    /**
     * Returns the requested property, or an empty array if the property is not present.
     */
    public static byte[] fetchPropertyOrEmptyArray(StateStore stateStore, String key) {
        if (stateStore.fetchPropertyKeys().contains(key)) {
            return stateStore.fetchProperty(key);
        } else {
            return new byte[0];
        }
    }

    /**
     * Fetches and returns all {@link Protos.TaskInfo}s for tasks needing recovery and in the list of
     * launchable Tasks.
     *
     * @return Terminated TaskInfos
     */
    public static Collection<Protos.TaskInfo> fetchTasksNeedingRecovery(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Set<String> launchableTaskNames) throws TaskException {

        return StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore).stream()
                .filter(taskInfo -> launchableTaskNames.contains(taskInfo.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Fetches and returns all {@link Protos.TaskInfo}s for tasks needing recovery.
     *
     * @return Terminated TaskInfos
     */
    public static Collection<Protos.TaskInfo> fetchTasksNeedingRecovery(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore) throws TaskException {

        Collection<Protos.TaskInfo> allInfos = stateStore.fetchTasks();
        Collection<Protos.TaskStatus> allStatuses = stateStore.fetchStatuses();

        Map<Protos.TaskID, Protos.TaskStatus> statusMap = new HashMap<>();
        for (Protos.TaskStatus status : allStatuses) {
            statusMap.put(status.getTaskId(), status);
        }

        List<Protos.TaskInfo> results = new ArrayList<>();
        for (Protos.TaskInfo info : allInfos) {
            Protos.TaskStatus status = statusMap.get(info.getTaskId());
            if (status == null) {
                continue;
            }

            Optional<TaskSpec> taskSpec = TaskUtils.getTaskSpec(configStore, info);
            if (!taskSpec.isPresent()) {
                throw new TaskException("Failed to determine TaskSpec from TaskInfo: " + info);
            }

            boolean markedFailed = FailureUtils.isPermanentlyFailed(info);
            boolean isPermanentlyFailed = markedFailed && taskSpec.get().getGoal() == GoalState.RUNNING;

            if (TaskUtils.needsRecovery(taskSpec.get(), status) || isPermanentlyFailed) {

                LOGGER.info(
                        "Task: '{}' needs recovery " +
                                "with status: {}, " +
                                "marked failed: {}, " +
                                "goal state: {}, " +
                                "permanently failed: {}.",
                        taskSpec.get().getName(),
                        TextFormat.shortDebugString(status),
                        markedFailed,
                        taskSpec.get().getGoal().name(),
                        isPermanentlyFailed);
                results.add(info);
            }
        }
        return results;
    }

    /**
     * Returns all {@link Protos.TaskInfo}s associated with the provided {@link PodInstance}, or an empty list if none
     * were found.
     *
     * @throws StateStoreException in the event of an IO error other than missing tasks
     */
    public static Collection<Protos.TaskInfo> fetchPodTasks(StateStore stateStore, PodInstance podInstance)
            throws StateStoreException {
        Collection<String> taskInfoNames = podInstance.getPod().getTasks().stream()
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());

        return taskInfoNames.stream()
                .map(name -> stateStore.fetchTask(name))
                .filter(taskInfo -> taskInfo.isPresent())
                .map(taskInfo -> taskInfo.get())
                .collect(Collectors.toList());
    }

    /**
     * Verifies that the supplied TaskStatus corresponds to a single TaskInfo in the provided StateStore and returns the
     * TaskInfo.
     *
     * @return The singular {@link Protos.TaskInfo} if it is present
     * @throws StateStoreException if zero or multiple corresponding {@link Protos.TaskInfo}s are found
     */
    public static String getTaskName(StateStore stateStore, Protos.TaskStatus taskStatus)
            throws StateStoreException {
        Optional<Protos.TaskInfo> taskInfoOptional = Optional.empty();

        for (Protos.TaskInfo taskInfo : stateStore.fetchTasks()) {
            if (taskInfo.getTaskId().getValue().equals(taskStatus.getTaskId().getValue())) {
                if (taskInfoOptional.isPresent()) {
                    LOGGER.error("Found duplicate TaskIDs in Task{} and Task {}",
                            taskInfoOptional.get(), taskInfo.getName());
                    throw new StateStoreException(Reason.LOGIC_ERROR, String.format(
                            "There are more than one tasks with TaskID: %s", taskStatus));
                } else {
                    taskInfoOptional = Optional.of(taskInfo);
                }
            }
        }

        if (!taskInfoOptional.isPresent()) {
            throw new StateStoreException(Reason.NOT_FOUND, String.format(
                    "Failed to find a task with TaskID: %s", taskStatus));
        }

        return taskInfoOptional.get().getName();
    }

    /**
     * TaskInfo and TaskStatus objects referring to the same Task name are not written atomically.
     * It is therefore possible for the states across these elements to become out of sync.  While the scheduler process
     * is up they remain in sync.  This method produces an initial synchronized state.
     *
     * For example:
     * <ol>
     * <li>TaskInfo(name=foo, id=1) is written</li>
     * <li>TaskStatus(name=foo, id=1, status=RUNNING) is written</li>
     * <li>Task foo is reconfigured/relaunched</li>
     * <li>TaskInfo(name=foo, id=2) is written</li>
     * <li>Scheduler is restarted before new TaskStatus is written</li>
     * <li>Scheduler comes back and sees TaskInfo(name=foo, id=2) and TaskStatus(name=foo, id=1, status=RUNNING)</li>
     * </ol>
     */
    static void repairTaskIDs(StateStore stateStore) {
        Map<String, Protos.TaskStatus> repairedStatuses = new HashMap<>();
        List<Protos.TaskInfo> repairedTasks = new ArrayList<>();

        for (Protos.TaskInfo task : stateStore.fetchTasks()) {
            Optional<Protos.TaskStatus> statusOptional = stateStore.fetchStatus(task.getName());

            if (statusOptional.isPresent()) {
                Protos.TaskStatus status = statusOptional.get();
                if (!status.getTaskId().equals(task.getTaskId())) {
                    LOGGER.warn(
                            "Found StateStore status inconsistency for task {}: task.taskId={}, taskStatus.taskId={}",
                            task.getName(), task.getTaskId(), status.getTaskId());
                    repairedTasks.add(task.toBuilder().setTaskId(status.getTaskId()).build());
                    repairedStatuses.put(
                            task.getName(), status.toBuilder().setState(Protos.TaskState.TASK_FAILED).build());
                }
            } else {
                LOGGER.warn(
                        "Found StateStore status inconsistency for task {}: task.taskId={}, no status",
                        task.getName(), task.getTaskId());
                Protos.TaskStatus status = Protos.TaskStatus.newBuilder()
                        .setTaskId(task.getTaskId())
                        .setState(Protos.TaskState.TASK_FAILED)
                        .setMessage("Assuming failure for inconsistent TaskIDs")
                        .build();
                repairedStatuses.put(task.getName(), status);
            }
        }

        stateStore.storeTasks(repairedTasks);
        repairedStatuses.entrySet().stream()
                .filter(statusEntry -> !statusEntry.getValue().getTaskId().getValue().equals(""))
                .forEach(statusEntry -> stateStore.storeStatus(statusEntry.getKey(), statusEntry.getValue()));
    }

    /**
     * Returns the current value of the 'uninstall' property in the provided {@link StateStore}.
     */
    public static boolean isUninstalling(StateStore stateStore) throws StateStoreException {
        return fetchBooleanProperty(stateStore, UNINSTALLING_PROPERTY_KEY);
    }

    /**
     * Sets an 'uninstall' property in the provided {@link StateStore} to {@code true}.
     */
    public static void setUninstalling(StateStore stateStore) {
        setBooleanProperty(stateStore, UNINSTALLING_PROPERTY_KEY, true);
    }

    @VisibleForTesting
    protected static boolean fetchBooleanProperty(StateStore stateStore, String propertyName) {
        byte[] bytes = fetchPropertyOrEmptyArray(stateStore, propertyName);
        if (bytes.length == 0) {
            return false;
        } else {
            try {
                return new JsonSerializer().deserialize(bytes, Boolean.class);
            } catch (IOException e) {
                LOGGER.error(String.format("Error converting property '%s' to boolean.", propertyName), e);
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, e);
            }
        }
    }

    private static void setBooleanProperty(StateStore stateStore, String propertyName, boolean value) {
        stateStore.storeProperty(propertyName, new JsonSerializer().serialize(value));
    }

    /**
     * Stores a TaskStatus as a Property in the provided state store.
     */
    public static void storeTaskStatusAsProperty(StateStore stateStore, String taskName, Protos.TaskStatus taskStatus)
            throws StateStoreException {
        stateStore.storeProperty(taskName + PROPERTY_TASK_INFO_SUFFIX, taskStatus.toByteArray());
    }

    /**
     * Returns an Optional<TaskStatus> from the properties in the provided state store for the specified
     * task name.
     */
    public static Optional<Protos.TaskStatus> getTaskStatusFromProperty(StateStore stateStore, String taskName) {
        try {
            return Optional.of(Protos.TaskStatus.parseFrom(
                    stateStore.fetchProperty(taskName + PROPERTY_TASK_INFO_SUFFIX)));
        } catch (Exception e) {
            // Broadly catch exceptions to handle:
            // Invalid TaskStatuses
            // StateStoreExceptions
            LOGGER.error("Unable to decode TaskStatus for taskName=" + taskName, e);
            return Optional.empty();
        }
    }

    /**
     * Sets whether the service has previously completed deployment.
     */
    public static void setDeploymentWasCompleted(StateStore stateStore) {
        stateStore.storeProperty(LAST_COMPLETED_UPDATE_TYPE_KEY, DEPLOYMENT_TYPE);
    }

    /**
     * Gets whether the service has previously completed deployment. If this is {@code true}, then any configuration
     * changes should be treated as an update rather than a new deployment.
     */
    public static boolean getDeploymentWasCompleted(StateStore stateStore) {
        return Arrays.equals(fetchPropertyOrEmptyArray(stateStore, LAST_COMPLETED_UPDATE_TYPE_KEY), DEPLOYMENT_TYPE);
    }
}
