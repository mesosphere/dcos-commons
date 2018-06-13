package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
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
import java.util.stream.Collectors;

public class OfferUtilsTest {

    @Mock
    private SchedulerDriver mockSchedulerDriver;

    private static final List<Protos.Offer> OFFERS = getOffers();
    private static final List<OfferRecommendation> OFFER_RECOMMENDATIONS = OFFERS.stream()
            .map(offer -> toOfferRecommendation(offer))
            .collect(Collectors.toList());

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testFilterAcceptedOffers() {
        final Protos.Offer unAcceptedOffer = OFFERS.get(1);
        final List<Protos.Offer> unacceptedOffers =
                OfferUtils.filterOutAcceptedOffers(OFFERS, Collections.singletonList(OFFER_RECOMMENDATIONS.get(0)));
        Assert.assertNotNull(unacceptedOffers);
        Assert.assertEquals(1, unacceptedOffers.size());
        Assert.assertEquals(unAcceptedOffer.getId(), unacceptedOffers.get(0).getId());
    }

    @Test
    public void testFilterAcceptedOffersNoAccepted() {
        final List<Protos.Offer> unacceptedOffers = OfferUtils.filterOutAcceptedOffers(OFFERS, Collections.emptyList());
        Assert.assertNotNull(unacceptedOffers);
        Assert.assertEquals(2, unacceptedOffers.size());
    }

    @Test
    public void testFilterAcceptedOffersAllAccepted() {
        final List<Protos.Offer> unacceptedOffers = OfferUtils.filterOutAcceptedOffers(
                OFFERS,
                Arrays.asList(OFFER_RECOMMENDATIONS.get(0), OFFER_RECOMMENDATIONS.get(1)));
        Assert.assertNotNull(unacceptedOffers);
        Assert.assertEquals(0, unacceptedOffers.size());
    }

    @Test
    public void testFilterAcceptedOffersAcceptedInvalidId() {
        final List<Protos.Offer> unacceptedOffers = OfferUtils.filterOutAcceptedOffers(
                OFFERS,
                Collections.singletonList(toOfferRecommendation(Protos.Offer.newBuilder(OfferTestUtils.getOffers(
                        Arrays.asList(
                                ResourceTestUtils.getUnreservedCpus(2),
                                ResourceTestUtils.getUnreservedMem(1000),
                                ResourceTestUtils.getUnreservedDisk(10000))).get(0))
                        .setId(Protos.OfferID.newBuilder().setValue("abc"))
                        .build())));
        Assert.assertNotNull(unacceptedOffers);
        Assert.assertEquals(2, unacceptedOffers.size());
    }

    private static List<Protos.Offer> getOffers() {
        final List<Protos.Offer> offers = new ArrayList<>();
        offers.addAll(OfferTestUtils.getOffers(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedCpus(2),
                        ResourceTestUtils.getUnreservedMem(1000),
                        ResourceTestUtils.getUnreservedDisk(10000))));
        offers.add(Protos.Offer.newBuilder(OfferTestUtils.getOffers(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedCpus(2),
                        ResourceTestUtils.getUnreservedMem(1000),
                        ResourceTestUtils.getUnreservedDisk(10000))).get(0))
                .setId(Protos.OfferID.newBuilder().setValue("other-offer"))
                .build());
        return offers;
    }

    private static OfferRecommendation toOfferRecommendation(Protos.Offer offer) {
        return new OfferRecommendation() {
            @Override
            public Operation getOperation() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Offer getOffer() {
                return offer;
            }
        };
    }
}
