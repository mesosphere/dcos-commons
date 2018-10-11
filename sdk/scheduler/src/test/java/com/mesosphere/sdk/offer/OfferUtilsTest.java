package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OfferUtilsTest {

    @Mock
    private SchedulerDriver mockSchedulerDriver;

    private static final List<Protos.Offer> OFFERS = new ArrayList<>();
    private static final List<OfferRecommendation> OFFER_RECOMMENDATIONS = new ArrayList<>();
    static {
        List<Protos.Resource> resources = Arrays.asList(
                ResourceTestUtils.getUnreservedCpus(2),
                ResourceTestUtils.getUnreservedMem(1000),
                ResourceTestUtils.getUnreservedDisk(10000));

        Protos.Offer offer = Protos.Offer.newBuilder(OfferTestUtils.getOffer(resources))
                .setId(Protos.OfferID.newBuilder().setValue("no-operation"))
                .build();
        OFFERS.add(offer);
        // one recommendation for this offer, but no operation:
        OFFER_RECOMMENDATIONS.add(toOfferRecommendation(offer, false));

        offer = Protos.Offer.newBuilder(OfferTestUtils.getOffer(resources))
                .setId(Protos.OfferID.newBuilder().setValue("with-without-operation"))
                .build();
        OFFERS.add(offer);
        // two recommendations, one with and one without operation:
        OFFER_RECOMMENDATIONS.add(toOfferRecommendation(offer, false));
        OFFER_RECOMMENDATIONS.add(toOfferRecommendation(offer, true));

        offer = Protos.Offer.newBuilder(OfferTestUtils.getOffer(resources))
                .setId(Protos.OfferID.newBuilder().setValue("with-operation"))
                .build();
        OFFERS.add(offer);
        // one recommendation with operation:
        OFFER_RECOMMENDATIONS.add(toOfferRecommendation(offer, true));

        offer = Protos.Offer.newBuilder(OfferTestUtils.getOffer(resources))
                .setId(Protos.OfferID.newBuilder().setValue("no-recommendation"))
                .build();
        OFFERS.add(offer);
        // no recommendations for this offer
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testFilterAcceptedOffers() {
        final List<Protos.Offer> unacceptedOffers =
                OfferUtils.filterOutAcceptedOffers(OFFERS, Collections.singletonList(OFFER_RECOMMENDATIONS.get(2)));
        Assert.assertEquals(
                Arrays.asList("no-operation", "with-operation", "no-recommendation"),
                unacceptedOffers.stream().map(o -> o.getId().getValue()).collect(Collectors.toList()));
    }

    @Test
    public void testFilterAcceptedOffersNoAccepted() {
        final List<Protos.Offer> unacceptedOffers = OfferUtils.filterOutAcceptedOffers(OFFERS, Collections.emptyList());
        Assert.assertEquals(
                Arrays.asList("no-operation", "with-without-operation", "with-operation", "no-recommendation"),
                unacceptedOffers.stream().map(o -> o.getId().getValue()).collect(Collectors.toList()));
    }

    @Test
    public void testFilterAcceptedOffersAllAccepted() {
        final List<Protos.Offer> unacceptedOffers = OfferUtils.filterOutAcceptedOffers(OFFERS, OFFER_RECOMMENDATIONS);
        Assert.assertEquals(
                Arrays.asList("no-operation", "no-recommendation"),
                unacceptedOffers.stream().map(o -> o.getId().getValue()).collect(Collectors.toList()));
    }

    @Test
    public void testFilterAcceptedOffersAcceptedInvalidId() {
        List<Protos.Resource> resources = Arrays.asList(
                ResourceTestUtils.getUnreservedCpus(2),
                ResourceTestUtils.getUnreservedMem(1000),
                ResourceTestUtils.getUnreservedDisk(10000));

        final List<Protos.Offer> unacceptedOffers = OfferUtils.filterOutAcceptedOffers(
                OFFERS,
                Arrays.asList(
                        toOfferRecommendation(Protos.Offer.newBuilder(OfferTestUtils.getOffer(resources))
                                .setId(Protos.OfferID.newBuilder().setValue("abc"))
                                .build(),
                                false),
                        toOfferRecommendation(Protos.Offer.newBuilder(OfferTestUtils.getOffer(resources))
                                .setId(Protos.OfferID.newBuilder().setValue("def"))
                                .build(),
                                true)));
        Assert.assertEquals(
                Arrays.asList("no-operation", "with-without-operation", "with-operation", "no-recommendation"),
                unacceptedOffers.stream().map(o -> o.getId().getValue()).collect(Collectors.toList()));
    }

    private static OfferRecommendation toOfferRecommendation(Protos.Offer offer, boolean withOperation) {
        return new OfferRecommendation() {
            @Override
            public Optional<Protos.Offer.Operation> getOperation() {
                return withOperation
                        ? Optional.of(Protos.Offer.Operation.getDefaultInstance())
                        : Optional.empty();
            }

            @Override
            public Protos.SlaveID getAgentId() {
                return offer.getSlaveId();
            }

            @Override
            public Protos.OfferID getOfferId() {
                return offer.getId();
            }
        };
    }
}
