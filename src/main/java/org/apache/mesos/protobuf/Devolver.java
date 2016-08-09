package org.apache.mesos.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import org.apache.mesos.v1.Protos.FrameworkInfo;

/**
 * Utility class for devolving vX protos to v0.
 */
public class Devolver {
    private static <T extends Message> T devolve(Message message, Message.Builder builder) {
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

    public static org.apache.mesos.Protos.FrameworkInfo devolve(FrameworkInfo frameworkInfo) {
        return devolve(frameworkInfo, org.apache.mesos.Protos.FrameworkInfo.newBuilder());
    }
}
