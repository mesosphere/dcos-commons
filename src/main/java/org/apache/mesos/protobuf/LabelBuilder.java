package org.apache.mesos.protobuf;

import org.apache.mesos.Protos;

/**
 * Builder class for working with protobufs.  It includes 2 different approaches;
 * 1) static functions useful for developers that want helpful protobuf functions for Label.
 * 2) builder class
 * All builder classes provide access to the protobuf builder for capabilities beyond the included
 * helpful functions.
 * <p/>
 * This builds Label objects.
 */
public class LabelBuilder {

  Protos.Labels.Builder builder = Protos.Labels.newBuilder();

  public LabelBuilder addLabel(String name, String value) {
    builder.addLabels(createLabel(name, value));
    return this;
  }

  public LabelBuilder addLabels(Protos.Labels labels) {
    builder.addAllLabels(labels.getLabelsList());
    return this;
  }

  public Protos.Labels build() {
    return builder.build();
  }

  public Protos.Labels.Builder builder() {
    return builder;
  }

  public static Protos.Label createLabel(String name, String value) {
    return Protos.Label.newBuilder().setKey(name).setValue(value).build();
  }
}
