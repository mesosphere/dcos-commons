package org.apache.mesos.protobuf;

import org.apache.mesos.Protos;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Utility class for creating attributes.   This class reduces the overhead of protobuf and makes code
 * easier to read.
 */
public class AttributeUtil {

  public static Protos.Attribute createScalarAttribute(String name, double value) {
    return Protos.Attribute.newBuilder()
      .setName(name)
      .setType(Protos.Value.Type.SCALAR)
      .setScalar(Protos.Value.Scalar.newBuilder().setValue(value).build())
      .build();
  }

  public static Protos.Attribute createRangeAttribute(String name, long begin, long end) {
    Protos.Value.Range range = Protos.Value.Range.newBuilder().setBegin(begin).setEnd(end).build();
    return Protos.Attribute.newBuilder()
      .setName(name)
      .setType(Protos.Value.Type.RANGES)
      .setRanges(Protos.Value.Ranges.newBuilder().addRange(range))
      .build();
  }

  public static Protos.Attribute createTextAttribute(String name, String value) {
    return Protos.Attribute.newBuilder()
      .setName(name)
      .setType(Protos.Value.Type.TEXT)
      .setText(Protos.Value.Text.newBuilder().setValue(value).build())
      .build();
  }

  public static Protos.Attribute createTextAttributeSet(String name, String values) {
    return Protos.Attribute.newBuilder()
      .setName(name)
      .setType(Protos.Value.Type.SET)
      .setSet(Protos.Value.Set.newBuilder().addAllItem(new ArrayList<String>(Arrays.asList(values.split(",")))))
      .build();
  }
}
