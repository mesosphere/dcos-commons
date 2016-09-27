package org.apache.mesos.testutils;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;

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
        return getEmptyOfferBuilder().addAllResources(resources).build();
    }

    public static List<Protos.Offer> getOffers(Protos.Resource resource) {
        return getOffers(Arrays.asList(resource));
    }

    public static Protos.Offer getOffer(Protos.ExecutorID executorId, List<Protos.Resource> resources) {
        Protos.Offer.Builder builder = getEmptyOfferBuilder();
        builder.addAllResources(resources);

        if (executorId != null) {
            builder.addExecutorIds(executorId);
        }

        return builder.build();
    }

    /**
     * Minimum to keep required field errors away.
     */
    public static Protos.Offer.Builder getEmptyOfferBuilder() {
        return Protos.Offer.newBuilder()
                .setId(TestConstants.offerId)
                .setFrameworkId(TestConstants.frameworkId)
                .setSlaveId(TestConstants.agentId)
                .setHostname(TestConstants.hostname);
    }

    /**
     * Minimum to keep required field errors away.
     */
    public static void addResource(Offer.Builder o, String name, String role) {
        Resource.Builder b = o.addResourcesBuilder().setType(Value.Type.RANGES).setName(name);
        if (role != null) {
            b.setRole(role);
        }
    }

    /**
     * Minimum to keep required field errors away.
     */
    public static void addResource(Offer.Builder o, String name) {
        addResource(o, name, null);
    }
}
