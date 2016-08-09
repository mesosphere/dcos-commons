package org.apache.mesos.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;

import org.apache.mesos.Protos.FrameworkInfo;

/**
 * Utility class for evolving v0 protos to vX protos.
 */
public class Evolver {
    public static org.apache.mesos.v1.Protos.FrameworkInfo evolve(FrameworkInfo frameworkInfo) {
        byte[] data = frameworkInfo.toByteArray();
        try {
            return org.apache.mesos.v1.Protos.FrameworkInfo.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
