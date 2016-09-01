package org.apache.mesos.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.scheduler.Protos.Call;

/**
 * Utility class for evolving v0 protos to vX.
 */
public class EvolverDevolver {
    private static <T extends Message> T transform(Message message, Message.Builder builder) {
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
        return transform(frameworkInfo, org.apache.mesos.v1.Protos.FrameworkInfo.newBuilder());
    }

    public static org.apache.mesos.v1.Protos.FrameworkID evolve(Protos.FrameworkID frameworkID) {
        return transform(frameworkID, org.apache.mesos.v1.Protos.FrameworkID.newBuilder());
    }

    public static org.apache.mesos.v1.Protos.Credential evolve(Protos.Credential credential) {
        return transform(credential, org.apache.mesos.v1.Protos.Credential.newBuilder());
    }

    public static org.apache.mesos.v1.scheduler.Protos.Call evolve(Call call) {
        return transform(call, org.apache.mesos.v1.scheduler.Protos.Call.newBuilder());
    }

    public static Protos.FrameworkInfo devolve(
            org.apache.mesos.v1.Protos.FrameworkInfo frameworkInfo) {
        return transform(frameworkInfo, Protos.FrameworkInfo.newBuilder());
    }

    public static Protos.FrameworkID devolve(org.apache.mesos.v1.Protos.FrameworkID frameworkId) {
        return transform(frameworkId, org.apache.mesos.Protos.FrameworkID.newBuilder());
    }

    public static org.apache.mesos.scheduler.Protos.Event devolve(org.apache.mesos.v1.scheduler.Protos.Event event) {
        return transform(event, org.apache.mesos.scheduler.Protos.Event.newBuilder());
    }
}
