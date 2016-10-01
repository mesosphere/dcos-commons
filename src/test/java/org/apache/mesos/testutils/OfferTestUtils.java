package org.apache.mesos.testutils;

import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.List;

/**
 * This class provides utilities for tests concerned with Offers.
 */
public class OfferTestUtils {
    public static List<Protos.Offer> getOffers(List<Protos.Resource> resources) {
        return Arrays.asList(getOffer(resources));
    }

    public static Protos.Offer getOffer(Protos.Resource resource) {
        return getOffer(Arrays.asList(resource));
    }

    public static Protos.Offer getOffer(List<Protos.Resource> resources) {
        return getBuilder().addAllResources(resources).build();
    }

    public static List<Protos.Offer> getOffers(Protos.Resource resource) {
        return getOffers(Arrays.asList(resource));
    }

    public static Protos.Offer getOffer(Protos.ExecutorID executorId, List<Protos.Resource> resources) {
        Protos.Offer.Builder builder = getBuilder();
        builder.addAllResources(resources);

        if (executorId != null) {
            builder.addExecutorIds(executorId);
        }

        return builder.build();
    }

    private static Protos.Offer.Builder getBuilder() {
        return Protos.Offer.newBuilder()
                .setId(TestConstants.OFFER_ID)
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME);
    }
}
