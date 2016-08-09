package org.apache.mesos.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import org.apache.mesos.v1.Protos;

import java.util.ArrayList;
import java.util.List;

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

    public static org.apache.mesos.Protos.FrameworkInfo devolve(Protos.FrameworkInfo frameworkInfo) {
        return devolve(frameworkInfo, org.apache.mesos.Protos.FrameworkInfo.newBuilder());
    }

    public static List<org.apache.mesos.Protos.Offer> devolve(
            org.apache.mesos.v1.scheduler.Protos.Event.Offers offers) {
        List<org.apache.mesos.Protos.Offer> offerList = new ArrayList<>(offers.getOffersCount());
        for (Protos.Offer offer : offers.getOffersList()) {
            offerList.add(Devolver.devolve(offer));
        }
        return offerList;
    }

    public static org.apache.mesos.Protos.Offer devolve(Protos.Offer offer) {
        return devolve(offer, org.apache.mesos.Protos.Offer.newBuilder());
    }

    public static org.apache.mesos.Protos.OfferID devolve(Protos.OfferID offerId) {
        return devolve(offerId, org.apache.mesos.Protos.OfferID.newBuilder());
    }

    public static org.apache.mesos.Protos.TaskStatus devolve(Protos.TaskStatus taskStatus) {
        return devolve(taskStatus, org.apache.mesos.Protos.TaskStatus.newBuilder());
    }

    public static org.apache.mesos.Protos.SlaveID devolve(Protos.AgentID agentID) {
        return devolve(agentID, org.apache.mesos.Protos.SlaveID.newBuilder());
    }

    public static org.apache.mesos.Protos.ExecutorID devolve(Protos.ExecutorID executorID) {
        return devolve(executorID, org.apache.mesos.Protos.ExecutorID.newBuilder());
    }
}
