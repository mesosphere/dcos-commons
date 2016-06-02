package org.apache.mesos.protobuf;

import org.apache.mesos.Protos;

import java.util.List;
import java.util.Map;

/**
 * Builder class for working with protobufs.  It includes 2 different approaches;
 * 1) static functions useful for developers that want helpful protobuf functions for CommandInfo.
 * 2) builder class
 * All builder classes provide access to the protobuf builder for capabilities beyond the included
 * helpful functions.
 * <p/>
 * This builds CommandInfo objects.
 */
public class CommandInfoBuilder {

  private Protos.CommandInfo.Builder builder = Protos.CommandInfo.newBuilder();
  private EnvironmentBuilder environmentBuilder = new EnvironmentBuilder();

  public CommandInfoBuilder addUri(String uri) {
    builder.addUris(createCmdInfoUri(uri));
    return this;
  }

  public CommandInfoBuilder addEnvironmentVar(String key, String value) {
    environmentBuilder.addVariable(key, value);
    builder.setEnvironment(environmentBuilder.build());
    return this;
  }

  public CommandInfoBuilder addEnvironmentMap(Map<String, String> envMap) {
    environmentBuilder.addVariable(envMap);
    builder.setEnvironment(environmentBuilder.build());
    return this;
  }

  public CommandInfoBuilder addUriList(List<Protos.CommandInfo.URI> uriList) {
    builder.addAllUris(uriList);
    return this;
  }

  public CommandInfoBuilder setCommand(String cmd) {
    builder.setValue(cmd);
    return this;
  }

  public Protos.CommandInfo build() {
    return builder.build();
  }

  public Protos.CommandInfo.Builder builder() {
    return builder;
  }

  public static Protos.CommandInfo.Builder createCommandInfoBuilder() {
    return Protos.CommandInfo.newBuilder();
  }

  public static Protos.CommandInfo createCmdInfo(String cmd,
    List<Protos.CommandInfo.URI> uriList,
    List<Protos.Environment.Variable> executorEnvironment) {
    return createCommandInfoBuilder()
      .addAllUris(uriList)
      .setEnvironment(EnvironmentBuilder.createEnvironmentBuilder()
        .addAllVariables(executorEnvironment))
      .setValue(cmd)
      .build();
  }

  public static Protos.CommandInfo.URI createCmdInfoUri(String uri) {
    return Protos.CommandInfo.URI.newBuilder().setValue(uri).build();
  }
}
