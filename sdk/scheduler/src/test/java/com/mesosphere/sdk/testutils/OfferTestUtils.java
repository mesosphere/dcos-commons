package com.mesosphere.sdk.testutils;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;

import com.mesosphere.sdk.offer.Constants;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * This class provides utilities for tests concerned with Offers.
 */
@SuppressWarnings("deprecation")
public class OfferTestUtils {

    private OfferTestUtils() {
        // do not instantiate
    }

    public static List<Protos.Offer> getCompleteOffers(List<Protos.Resource> resources) {
        return Arrays.asList(getCompleteOffer(resources));
    }

    public static Protos.Offer getCompleteOffer(Protos.Resource resource) {
        return getCompleteOffer(Arrays.asList(resource));
    }

    public static List<Protos.Offer> getOffers(Protos.Resource resource) {
        return getOffers(Arrays.asList(resource));
    }

    public static List<Protos.Offer> getOffers(List<Protos.Resource> resources) {
        return Arrays.asList(getOffer(resources));
    }

    public static Protos.Offer getOffer(Protos.Resource resource) {
        return getOffer(Arrays.asList(resource));
    }

    public static Protos.Offer getOffer(Collection<Protos.Resource> resources) {
        return getEmptyOfferBuilder().addAllResources(resources).build();
    }

    /**
     * Get an offer that includes sufficient resources to launch the default executor in addition to some task.
     * @param resources The desired task resources to offer
     * @return An offer with both executor resources and the supplied resources available
     */
    public static Protos.Offer getCompleteOffer(List<Protos.Resource> resources) {
        return getCompleteOffer(resources, Constants.ANY_ROLE);
    }

    public static Protos.Offer getCompleteOffer(List<Protos.Resource> resources, String preReservedRole) {
        return getEmptyOfferBuilder()
                .addAllResources(getExecutorResources(preReservedRole))
                .addAllResources(resources)
                .build();
    }

    private static Collection<Resource> getExecutorResources(String preReservedRole) {
        return Arrays.asList(
                ResourceTestUtils.getUnreservedCpus(0.1, preReservedRole),
                ResourceTestUtils.getUnreservedMem(256, preReservedRole),
                ResourceTestUtils.getUnreservedDisk(512, preReservedRole));
    }

    public static List<Protos.Offer> getCompleteOffers(Protos.Resource resource) {
        return getCompleteOffers(Arrays.asList(resource));
    }

    public static Protos.Offer getCompleteOffer(Protos.ExecutorID executorId, List<Protos.Resource> resources) {
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
                .setId(TestConstants.OFFER_ID)
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME);
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
