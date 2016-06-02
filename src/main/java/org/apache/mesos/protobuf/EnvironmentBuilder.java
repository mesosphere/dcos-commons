package org.apache.mesos.protobuf;

import org.apache.mesos.Protos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builder class for working with protobufs.  It includes 2 different approaches;
 * 1) static functions useful for developers that want helpful protobuf functions for Environment.Builder.
 * 2) builder class
 * All builder classes provide access to the protobuf builder for capabilities beyond the included
 * helpful functions.
 * <p/>
 * This builds Environment objects usually used with ExecutorInfo.
 */
public class EnvironmentBuilder {

  Protos.Environment.Builder builder = Protos.Environment.newBuilder();

  public EnvironmentBuilder addVariable(String key, String value) {
    builder.addVariables(createEnvironment(key, value));
    return this;
  }

  public EnvironmentBuilder addVariable(Map<String, String> envMap) {
    builder.addAllVariables(createEnvironment(envMap));
    return this;
  }


  public Protos.Environment build() {
    return builder.build();
  }

  public Protos.Environment.Builder builder() {
    return builder;
  }

  public static Protos.Environment.Variable createEnvironment(String key, String value) {
    return Protos.Environment.Variable.newBuilder().setName(key).setValue(value).build();
  }

  public static List<Protos.Environment.Variable> createEnvironment(Map<String, String> envMap) {
    List<Protos.Environment.Variable> list = new ArrayList<>(envMap.size());
    for (Map.Entry<String, String> var : envMap.entrySet()) {
      list.add(createEnvironment(var.getKey(), var.getValue()));
    }
    return list;
  }

  public static List<Protos.Environment.Variable> createEnvironmentVarList() {
    return new ArrayList<Protos.Environment.Variable>();
  }

  public static Protos.Environment.Builder createEnvironmentBuilder() {
    return Protos.Environment.newBuilder();
  }
}
