package org.apache.mesos.protobuf;

import org.apache.mesos.Protos;

/**
 * Utility class for working with slaves.
 * This class reduces the overhead of protobuf and makes code
 * easier to read.
 */
public class SlaveUtil {
  public static Protos.SlaveID createSlaveId(String slaveID) {
    return Protos.SlaveID.newBuilder().setValue(slaveID).build();
  }
}
