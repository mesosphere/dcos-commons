package org.apache.mesos.protobuf;

import org.apache.mesos.Protos;

/**
 * Utility class for creating frameworkID.   This class reduces the overhead of protobuf and makes code
 * easier to read.
 */
public class FrameworkInfoUtil {

  public static Protos.FrameworkID createFrameworkId(String name) {
    return Protos.FrameworkID.newBuilder().setValue(name).build();
  }
}
