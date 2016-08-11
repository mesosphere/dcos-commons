package org.apache.mesos.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.scheduler.Protos.Call;

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

    public static org.apache.mesos.v1.Protos.FrameworkID evolve(Protos.FrameworkID frameworkID) {
        return evolve(frameworkID, org.apache.mesos.v1.Protos.FrameworkID.newBuilder());
    }

    public static org.apache.mesos.v1.Protos.Credential evolve(Protos.Credential credential) {
        return evolve(credential, org.apache.mesos.v1.Protos.Credential.newBuilder());
    }

    public static org.apache.mesos.v1.scheduler.Protos.Call evolve(Call call) {
        return evolve(call, org.apache.mesos.v1.scheduler.Protos.Call.newBuilder());
    }
}
