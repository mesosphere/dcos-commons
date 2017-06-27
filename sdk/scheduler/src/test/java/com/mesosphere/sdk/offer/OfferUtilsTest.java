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
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Mockito.verify;

public class OfferUtilsTest {
    public static final int SUFFICIENT_CPUS = 2;
    public static final int SUFFICIENT_MEM = 2000;
    public static final int SUFFICIENT_DISK = 10000;

    @Mock
    private SchedulerDriver mockSchedulerDriver;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testFilterAcceptedOffers() {
        final List<Protos.Offer> offers = getOffers(SUFFICIENT_CPUS, SUFFICIENT_MEM, SUFFICIENT_DISK);
        final Protos.Offer acceptedOffer = offers.get(0);
        final Protos.Offer unAcceptedOffer = offers.get(1);
        final List<Protos.Offer> unacceptedOffers = OfferUtils
                .filterOutAcceptedOffers(offers, Arrays.asList(acceptedOffer.getId()));
        Assert.assertNotNull(unacceptedOffers);
        Assert.assertEquals(1, unacceptedOffers.size());
        Assert.assertEquals(unAcceptedOffer.getId(), unacceptedOffers.get(0).getId());
    }

    @Test
    public void testFilterAcceptedOffersNoAccepted() {
        final List<Protos.Offer> offers = getOffers(SUFFICIENT_CPUS, SUFFICIENT_MEM, SUFFICIENT_DISK);
        final List<Protos.Offer> unacceptedOffers = OfferUtils
                .filterOutAcceptedOffers(offers, Arrays.asList());
        Assert.assertNotNull(unacceptedOffers);
        Assert.assertEquals(2, unacceptedOffers.size());
    }

    @Test
    public void testFilterAcceptedOffersAllAccepted() {
        final List<Protos.Offer> offers = getOffers(SUFFICIENT_CPUS, SUFFICIENT_MEM, SUFFICIENT_DISK);
        final List<Protos.Offer> unacceptedOffers = OfferUtils
                .filterOutAcceptedOffers(offers, Arrays.asList(offers.get(0).getId(), offers.get(1).getId()));
        Assert.assertNotNull(unacceptedOffers);
        Assert.assertEquals(0, unacceptedOffers.size());
    }

    @Test
    public void testFilterAcceptedOffersAcceptedInvalidId() {
        final List<Protos.Offer> offers = getOffers(SUFFICIENT_CPUS, SUFFICIENT_MEM, SUFFICIENT_DISK);
        final List<Protos.Offer> unacceptedOffers = OfferUtils
                .filterOutAcceptedOffers(offers, Arrays.asList(Protos.OfferID.newBuilder().setValue("abc").build()));
        Assert.assertNotNull(unacceptedOffers);
        Assert.assertEquals(2, unacceptedOffers.size());
    }

    @Test
    public void testDeclineOffers() {
        final List<Protos.Offer> offers = getOffers(SUFFICIENT_CPUS, SUFFICIENT_MEM, SUFFICIENT_DISK);
        final List<Protos.OfferID> offerIds = offers.stream().map(Protos.Offer::getId).collect(Collectors.toList());
        OfferUtils.declineOffers(mockSchedulerDriver, offers);
        verify(mockSchedulerDriver).declineOffer(offerIds.get(0));
        verify(mockSchedulerDriver).declineOffer(offerIds.get(1));
    }

    private List<Protos.Offer> getOffers(double cpus, double mem, double disk) {
        final ArrayList<Protos.Offer> offers = new ArrayList<>();
        offers.addAll(OfferTestUtils.getOffers(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedCpu(cpus),
                        ResourceTestUtils.getUnreservedMem(mem),
                        ResourceTestUtils.getUnreservedDisk(disk))));
        offers.add(Protos.Offer.newBuilder(OfferTestUtils.getOffers(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedCpu(cpus),
                        ResourceTestUtils.getUnreservedMem(mem),
                        ResourceTestUtils.getUnreservedDisk(disk))).get(0))
                .setId(Protos.OfferID.newBuilder().setValue("other-offer"))
                .build());
        return offers;
    }
}
