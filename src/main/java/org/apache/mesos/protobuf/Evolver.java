package org.apache.mesos.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import org.apache.mesos.Protos.FrameworkInfo;


/**
 * Utility class for evolving v0 protos to vX.
 */
public class Evolver {
    private static <T extends Message> T evolve(Message message, Message.Builder builder) {
        byte[] data = message.toByteArray();
        try {
            builder.mergeFrom(data);

            // NOTE: We need to use `buildPartial()` instead of `build()` because some required
            // fields might not be set and we don't want an exception to get thrown.
            return (T) builder.buildPartial();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public static org.apache.mesos.v1.Protos.FrameworkInfo evolve(FrameworkInfo frameworkInfo) {
        return evolve(frameworkInfo, org.apache.mesos.v1.Protos.FrameworkInfo.newBuilder());
    }
}
