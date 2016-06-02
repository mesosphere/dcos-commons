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
}
