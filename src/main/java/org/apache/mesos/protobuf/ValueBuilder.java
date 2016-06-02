package org.apache.mesos.protobuf;

import org.apache.mesos.Protos.Value;
import org.apache.mesos.Protos.Value.Type;

/**
 * Builder for Value Protobufs.
 */
public class ValueBuilder {

  Value.Builder builder = Value.newBuilder();

  public ValueBuilder(Type type) {
    builder.setType(type);
  }

  public ValueBuilder setScalar(Value.Scalar scalar) {
    builder.setScalar(scalar);
    return this;
  }

  public ValueBuilder setScalar(double value) {
    builder.setScalar(Value.Scalar.newBuilder().setValue(value).build());
    return this;
  }

  public ValueBuilder setRanges(Value.Ranges ranges) {
    builder.setRanges(ranges);
    return this;
  }

  public ValueBuilder setSet(Value.Set set) {
    builder.setSet(set);
    return this;
  }

  public ValueBuilder setText(Value.Text text) {
    builder.setText(text);
    return this;
  }

  public Value build() {
    return builder.build();
  }
}
