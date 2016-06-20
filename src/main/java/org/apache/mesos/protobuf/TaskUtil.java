package org.apache.mesos.protobuf;

import org.apache.mesos.Protos;

/**
 * Utility class for working with Tasks.
 * This class reduces the overhead of protobuf and makes code
 * easier to read.
 */
public class TaskUtil {
  public static Protos.TaskID createTaskId(String taskId) {
    return Protos.TaskID.newBuilder().setValue(taskId).build();
  }

  public static boolean isTerminalState(Protos.TaskState state) {
    return state == Protos.TaskState.TASK_ERROR
            || state == Protos.TaskState.TASK_FAILED
            || state == Protos.TaskState.TASK_FINISHED
            || state == Protos.TaskState.TASK_KILLED
            || state == Protos.TaskState.TASK_LOST;
  }
}
