package org.apache.mesos.testutils;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskUtils;

import java.util.Arrays;
import java.util.List;

/**
 * This class provides utility method for Tests concerned with Tasks.
 */
public class TaskTestUtils {
    public static Protos.TaskInfo getTaskInfo(Protos.Resource resource) {
        return getTaskInfo(Arrays.asList(resource));
    }

    public static Protos.TaskInfo getTaskInfo(List<Protos.Resource> resources) {
        Protos.TaskInfo.Builder builder = Protos.TaskInfo.newBuilder()
                .setTaskId(TestConstants.TASK_ID)
                .setName(TestConstants.TASK_NAME)
                .setSlaveId(TestConstants.AGENT_ID)
                .setCommand(TestConstants.COMMAND_INFO);
        builder = TaskUtils.setTaskType(builder, TestConstants.TASK_TYPE);
        return builder.addAllResources(resources).build();
    }

    public static Protos.ExecutorInfo getExecutorInfo(Protos.Resource resource) {
        return getExecutorInfo(Arrays.asList(resource));
    }

    public static Protos.ExecutorInfo getExecutorInfo(List<Protos.Resource> resources) {
        return getExecutorInfoBuilder().addAllResources(resources).build();
    }

    public static Protos.ExecutorInfo getExistingExecutorInfo(Protos.Resource resource) {
        return getExecutorInfoBuilder()
                .addResources(resource)
                .setExecutorId(TestConstants.EXECUTOR_ID)
                .build();
    }

    public static Protos.Environment.Variable createEnvironmentVariable(String key, String value) {
        return Protos.Environment.Variable.newBuilder().setName(key).setValue(value).build();
    }

    private static Protos.ExecutorInfo.Builder getExecutorInfoBuilder() {
        Protos.CommandInfo cmd = Protos.CommandInfo.newBuilder().build();
        return Protos.ExecutorInfo.newBuilder()
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(""))
                .setName(TestConstants.EXECUTOR_NAME)
                .setCommand(cmd);
    }

    public static Protos.TaskStatus generateStatus(
            Protos.TaskID taskID,
            Protos.TaskState taskState) {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(taskState)
                .build();
    }
}
