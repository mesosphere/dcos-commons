package com.mesosphere.sdk.offer;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.mesos.Protos.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.Constants.*;

/**
 * Various utility methods for manipulating data in {@link TaskInfo}s.
 */
public class CommonTaskUtils {

    private CommonTaskUtils() {
        // do not instantiate
    }

    /**
     * Converts the unique {@link TaskID} into a Framework defined task name.
     * <p>
     * For example: "instance-0__aoeu5678" => "instance-0"
     */
    public static String toTaskName(TaskID taskId) throws TaskException {
        int underScoreIndex = taskId.getValue().lastIndexOf(TASK_NAME_DELIM);

        if (underScoreIndex == -1) {
            throw new TaskException(String.format(
                    "TaskID '%s' is malformed.  Expected '%s' to extract TaskName from TaskID.  "
                            + "TaskIDs should be generated with CommonTaskUtils.toTaskId().", taskId, TASK_NAME_DELIM));
        }

        return taskId.getValue().substring(0, underScoreIndex);
    }

    /**
     * Converts the Framework defined task name into a unique {@link TaskID}.
     * <p>
     * For example: "instance-0" => "instance-0__aoeu5678"
     */
    public static TaskID toTaskId(String taskName) {
        return TaskID.newBuilder()
                .setValue(taskName + TASK_NAME_DELIM + UUID.randomUUID())
                .build();
    }

    public static TaskID emptyTaskId() {
        return TaskID.newBuilder().setValue("").build();
    }

    public static SlaveID emptyAgentId() {
        return SlaveID.newBuilder().setValue("").build();
    }

    public static ExecutorID emptyExecutorId() {
        return ExecutorID.newBuilder().setValue("").build();
    }

    /**
     * Extracts the environment variables given in the {@link Environment}.
     *
     * @param environment The {@link Environment} to extract environment variables from
     * @return The map containing environment variables
     */
    public static Map<String, String> fromEnvironmentToMap(Environment environment) {
        Map<String, String> map = new HashMap<>();

        final List<Environment.Variable> variables = environment.getVariablesList();

        for (Environment.Variable variable : variables) {
            map.put(variable.getName(), variable.getValue());
        }

        return map;
    }

    /**
     * Extracts and puts environment variables from the given map to a {@link Environment}.
     *
     * @param environmentMap The map to extract environment variables from
     * @return The {@link Environment} containing the extracted environment variables
     */
    public static Environment.Builder fromMapToEnvironment(Map<String, String> environmentMap) {
        if (environmentMap == null) {
            return Environment.newBuilder();
        }

        Collection<Environment.Variable> vars = environmentMap
                .entrySet()
                .stream()
                .map(entrySet -> Environment.Variable.newBuilder()
                        .setName(entrySet.getKey())
                        .setValue(entrySet.getValue()).build())
                .collect(Collectors.toList());

        return Environment.newBuilder().addAllVariables(vars);
    }

    /**
     * Mesos requirements do not allow a TaskInfo to simultaneously have a Command and Executor.  In order to
     * workaround this we encapsulate a TaskInfo's Command field in an ExecutorInfo and store it in the data field of
     * the TaskInfo.
     *
     * Unpacked:
     * - taskInfo
     *   - executor
     *   - data: custom
     *   - command
     *
     * Packed:
     * - taskInfo
     *   - executor
     *   - data: serialized executorinfo
     *     - data: custom
     *     - command
     *
     * @see #unpackTaskInfo(TaskInfo)
     */
    public static TaskInfo packTaskInfo(TaskInfo taskInfo) {
        if (!taskInfo.hasExecutor()) {
            return taskInfo;
        } else {
            ExecutorInfo.Builder executorInfoBuilder = ExecutorInfo.newBuilder()
                    .setExecutorId(ExecutorID.newBuilder().setValue(COMMAND_DATA_PACKAGE_EXECUTORID));

            if (taskInfo.hasCommand()) {
                executorInfoBuilder.setCommand(taskInfo.getCommand());
            }

            if (taskInfo.hasData()) {
                executorInfoBuilder.setData(taskInfo.getData());
            }

            return TaskInfo.newBuilder(taskInfo)
                    .setData(executorInfoBuilder.build().toByteString())
                    .clearCommand()
                    .build();
        }
    }

    /**
     * This method reverses the work done in {@link #packTaskInfo(TaskInfo)} such that the original
     * TaskInfo is regenerated. If the provided {@link TaskInfo} doesn't appear to have packed data
     * then this operation does nothing.
     *
     * TODO(nickbp): Make this function only visible to Custom Executor code. Scheduler shouldn't ever call it.
     *
     * @see #packTaskInfo(TaskInfo)
     */
    public static TaskInfo unpackTaskInfo(TaskInfo taskInfo) {
        if (!taskInfo.hasData() || !taskInfo.hasExecutor()) {
            return taskInfo;
        } else {
            TaskInfo.Builder taskBuilder = TaskInfo.newBuilder(taskInfo);
            ExecutorInfo pkgExecutorInfo;
            try {
                pkgExecutorInfo = ExecutorInfo.parseFrom(taskInfo.getData());
            } catch (InvalidProtocolBufferException e) {
                // This TaskInfo has a data field, but it doesn't parse as an ExecutorInfo. Not a packed TaskInfo?
                // TODO(nickbp): This try/catch should be removed once CuratorStateStore is no longer speculatively
                //               unpacking all TaskInfos.
                return taskInfo;
            }

            if (pkgExecutorInfo.hasCommand()) {
                taskBuilder.setCommand(pkgExecutorInfo.getCommand());
            }

            if (pkgExecutorInfo.hasData()) {
                taskBuilder.setData(pkgExecutorInfo.getData());
            } else {
                taskBuilder.clearData();
            }

            return taskBuilder.build();
        }
    }
}
