package com.mesosphere.sdk.scheduler.framework;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import com.mesosphere.sdk.offer.OfferUtils;
import com.mesosphere.sdk.scheduler.Driver;

public class OfferProcessorTest {

    @Test
    public void testsWritten() {
        Assert.fail("TODO");
    }

    /*
    @Test
    public void testDeclineOffers() {
        final List<Protos.Offer> offers = getOffers(SUFFICIENT_CPUS, SUFFICIENT_MEM, SUFFICIENT_DISK);
        final List<Protos.OfferID> offerIds = offers.stream().map(Protos.Offer::getId).collect(Collectors.toList());
        Driver.setDriver(mockSchedulerDriver);
        OfferUtils.declineLong(offers);
        verify(mockSchedulerDriver).declineOffer(eq(offerIds.get(0)), any());
        verify(mockSchedulerDriver).declineOffer(eq(offerIds.get(1)), any());
    }
    */
}
