package org.apache.mesos.protobuf;

import com.google.protobuf.ByteString;
import org.apache.mesos.Protos;

import java.util.List;

/**
 * Builder class for working with protobufs.  It includes 2 different approaches;
 * 1) static functions useful for developers that want helpful protobuf functions for TaskInfo.
 * 2) builder class
 * All builder classes provide access to the protobuf builder for capabilities beyond the included
 * helpful functions.
 * <p/>
 * This builds TaskInfo objects.
 */
public class TaskInfoBuilder {

  Protos.TaskInfo.Builder builder = Protos.TaskInfo.newBuilder();

  // min required fields to create a taskInfo
  public TaskInfoBuilder(String taskId, String name, String slaveId) {
    setId(taskId);
    setName(name);
    setSlaveId(slaveId);
  }

  public TaskInfoBuilder setId(String taskId) {
    builder.setTaskId(TaskUtil.createTaskId(taskId));
    return this;
  }

  public TaskInfoBuilder setName(String name) {
    builder.setName(name);
    return this;
  }

  public TaskInfoBuilder setSlaveId(String slaveId) {
    builder.setSlaveId(SlaveUtil.createSlaveId(slaveId));
    return this;
  }

  public TaskInfoBuilder setCommand(Protos.CommandInfo commandInfo) {
    builder.setCommand(commandInfo);
    return this;
  }

  public TaskInfoBuilder setLabels(Protos.Labels labels) {
    builder.setLabels(labels);
    return this;
  }

  public TaskInfoBuilder setExecutorInfo(Protos.ExecutorInfo executorInfo) {
    builder.setExecutor(executorInfo);
    return this;
  }

  public TaskInfoBuilder addAllResources(List<Protos.Resource> resourceList) {
    builder.addAllResources(resourceList);
    return this;
  }

  public TaskInfoBuilder addResource(Protos.Resource resource) {
    builder.addResources(resource);
    return this;
  }

  public TaskInfoBuilder setData(String data) {
    builder.setData(ByteString.copyFromUtf8(data));
    return this;
  }

  public Protos.TaskInfo build() {
    return builder.build();
  }

  public Protos.TaskInfo.Builder builder() {
    return builder;
  }
}
