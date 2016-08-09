package org.apache.mesos.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;

import org.apache.mesos.v1.Protos.FrameworkInfo;

/**
 * Utility class for devolving vX protos to v0 protos.
 */
public class Devolver {
    public static org.apache.mesos.Protos.FrameworkInfo devolve(FrameworkInfo frameworkInfo) {
        byte[] data = frameworkInfo.toByteArray();
        try {
            return org.apache.mesos.Protos.FrameworkInfo.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
