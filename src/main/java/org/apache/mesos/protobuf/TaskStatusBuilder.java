package org.apache.mesos.protobuf;

import com.google.protobuf.ByteString;
import org.apache.mesos.Protos;

/**
 * Builder class for working with protobufs.  It includes 2 different approaches;
 * 1) static functions useful for developers that want helpful protobuf functions for TaskStatus.
 * 2) builder class
 * All builder classes provide access to the protobuf builder for capabilities beyond the included
 * helpful functions.
 * <p/>
 * This builds TaskStatus objects.
 */
public class TaskStatusBuilder {

  Protos.TaskStatus.Builder builder = createTaskStatusBuilder();
  LabelBuilder labelBuilder = new LabelBuilder();

  public TaskStatusBuilder() {
  }

  public TaskStatusBuilder(Protos.TaskStatus prototype) {
    builder = createTaskStatusBuilder(prototype);
  }

  public TaskStatusBuilder setTaskId(String taskId) {
    setTaskId(TaskUtil.createTaskId(taskId));
    return this;
  }

  public TaskStatusBuilder setTaskId(Protos.TaskID taskId) {
    builder.setTaskId(taskId);
    return this;
  }

  public TaskStatusBuilder setSlaveId(String slaveId) {
    builder.setSlaveId(SlaveUtil.createSlaveId(slaveId));
    return this;
  }

  public TaskStatusBuilder setState(Protos.TaskState state) {
    builder.setState(state);
    return this;
  }

  public TaskStatusBuilder setMessage(String message) {
    builder.setMessage(message);
    return this;
  }

  public TaskStatusBuilder addLabel(String key, String value) {
    labelBuilder.addLabel(key, value);
    builder.setLabels(labelBuilder.build());
    return this;
  }

  public TaskStatusBuilder setLabels(Protos.Labels labels) {
    labelBuilder.addLabels(labels);
    builder.setLabels(labelBuilder.build());
    return this;
  }

  public TaskStatusBuilder setData(ByteString data) {
    builder.setData(data);
    return this;
  }

  public static Protos.TaskStatus.Builder createTaskStatusBuilder() {
    return Protos.TaskStatus.newBuilder();
  }

  public static Protos.TaskStatus.Builder createTaskStatusBuilder(Protos.TaskStatus prototype) {
    return Protos.TaskStatus.newBuilder(prototype);
  }

  public static TaskStatusBuilder newBuilder() {
    return new TaskStatusBuilder();
  }

  public Protos.TaskStatus build() {
    return builder.build();
  }

  public Protos.TaskStatus.Builder builder() {
    return builder;
  }

  public static Protos.TaskStatus createTaskStatus(String taskId, Protos.TaskState state) {
    return createTaskStatus(TaskUtil.createTaskId(taskId), state);
  }

  public static Protos.TaskStatus createTaskStatus(Protos.TaskID taskId, Protos.TaskState state) {
    return new TaskStatusBuilder().setTaskId(taskId).setState(state).build();
  }

  public static Protos.TaskStatus createTaskStatus(String taskId, String slaveId,
    Protos.TaskState state, String message) {
    return createTaskStatus(TaskUtil.createTaskId(taskId), slaveId, state, message);
  }

  public static Protos.TaskStatus createTaskStatus(Protos.TaskID taskId, String slaveId,
    Protos.TaskState state, String message) {
    return new TaskStatusBuilder()
      .setTaskId(taskId)
      .setState(state)
      .setSlaveId(slaveId)
      .setMessage(message)
      .build();
  }
}
