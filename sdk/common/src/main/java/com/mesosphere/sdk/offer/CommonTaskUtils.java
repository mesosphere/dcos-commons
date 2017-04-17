package com.mesosphere.sdk.offer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.mesos.Protos.Environment;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;

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

        for (Environment.Variable variable : environment.getVariablesList()) {
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
}
