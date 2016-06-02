package org.apache.mesos.protobuf;

import org.apache.mesos.Protos;

import java.util.List;

/**
 * Builder class for working with protobufs.  It includes 2 different approaches;
 * 1) static functions useful for developers that want helpful protobuf functions for ExecutorInfo.
 * 2) builder class
 * All builder classes provide access to the protobuf builder for capabilities beyond the included
 * helpful functions.
 * <p/>
 * This builds ExecutorInfo objects.
 */
public class ExecutorInfoBuilder {

  private Protos.ExecutorInfo.Builder builder = Protos.ExecutorInfo.newBuilder();

  public ExecutorInfoBuilder(String executorId, Protos.CommandInfo cmdInfo) {
    setExecutorId(executorId);
    builder.setCommand(cmdInfo);
  }

  public ExecutorInfoBuilder(String executorId, String name, Protos.CommandInfo cmdInfo) {
    this(executorId, cmdInfo);
    setName(name);
  }

  public ExecutorInfoBuilder setExecutorId(String executorId) {
    builder.setExecutorId(createExecutorId(executorId));
    return this;
  }

  public ExecutorInfoBuilder setName(String name) {
    builder.setName(name);
    return this;
  }

  public ExecutorInfoBuilder addAllResources(List<Protos.Resource> resourceList) {
    builder.addAllResources(resourceList);
    return this;
  }

  public ExecutorInfoBuilder addResource(Protos.Resource resource) {
    builder.addResources(resource);
    return this;
  }

  public Protos.ExecutorInfo build() {
    return builder.build();
  }

  public Protos.ExecutorInfo.Builder builder() {
    return builder;
  }

  public static Protos.ExecutorID createExecutorId(String executorId) {
    return Protos.ExecutorID.newBuilder().setValue(executorId).build();
  }

  public static Protos.ExecutorInfo.Builder createExecutorInfoBuilder() {
    return Protos.ExecutorInfo.newBuilder();
  }
}
