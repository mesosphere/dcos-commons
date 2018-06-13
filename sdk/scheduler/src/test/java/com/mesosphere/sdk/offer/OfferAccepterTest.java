package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;

import com.mesosphere.sdk.framework.Driver;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

public class OfferAccepterTest {

    private static final Protos.Offer OFFER_A = Protos.Offer.newBuilder(Protos.Offer.getDefaultInstance())
            .setHostname("hostA")
            .setSlaveId(Protos.SlaveID.newBuilder().setValue("agentA").build())
            .setId(Protos.OfferID.newBuilder().setValue("offerA"))
            .setFrameworkId(Protos.FrameworkID.newBuilder().setValue("fwkA"))
            .build();
    private static final Protos.Offer OFFER_B = Protos.Offer.newBuilder(Protos.Offer.getDefaultInstance())
            .setHostname("hostB")
            .setSlaveId(Protos.SlaveID.newBuilder().setValue("agentB").build())
            .setId(Protos.OfferID.newBuilder().setValue("offerB"))
            .setFrameworkId(Protos.FrameworkID.newBuilder().setValue("fwkB"))
            .build();

    private static final DestroyOfferRecommendation DESTROY_A =
            new DestroyOfferRecommendation(OFFER_A, ResourceTestUtils.getUnreservedCpus(1.0));
    private static final DestroyOfferRecommendation DESTROY_B =
            new DestroyOfferRecommendation(OFFER_B, ResourceTestUtils.getUnreservedCpus(2.0));
    private static final UnreserveOfferRecommendation UNRESERVE_A =
            new UnreserveOfferRecommendation(OFFER_A, ResourceTestUtils.getUnreservedCpus(1.1));
    private static final UnreserveOfferRecommendation UNRESERVE_B =
            new UnreserveOfferRecommendation(OFFER_B, ResourceTestUtils.getUnreservedCpus(2.1));

    private static final List<OfferRecommendation> ALL_RECOMMENDATIONS =
            Arrays.asList(DESTROY_A, DESTROY_B, UNRESERVE_A, UNRESERVE_B);

    private static final OfferAccepter ACCEPTER = new OfferAccepter();

    @Mock private SchedulerDriver mockDriver;
    @Captor private ArgumentCaptor<Collection<Protos.OfferID>> offerIdCaptor;
    @Captor private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationCaptor;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testAcceptEmptyList() {
        Driver.setDriver(mockDriver);

        ACCEPTER.accept(Collections.emptyList());

        verifyZeroInteractions(mockDriver);
    }

    @Test
    public void testGroupRecommendationsByAgent() {
        final Map<String, List<OfferRecommendation>> group = OfferAccepter.groupByAgent(ALL_RECOMMENDATIONS);
        Assert.assertEquals(2, group.size());

        List<OfferRecommendation> recs = group.get("agentA");
        Assert.assertEquals(2, recs.size());
        Assert.assertEquals(DESTROY_A, recs.get(0));
        Assert.assertEquals(UNRESERVE_A, recs.get(1));

        recs = group.get("agentB");
        Assert.assertEquals(2, recs.size());
        Assert.assertEquals(DESTROY_B, recs.get(0));
        Assert.assertEquals(UNRESERVE_B, recs.get(1));
    }

    @Test
    public void testAcceptRecommendationsByAgent() {
        Driver.setDriver(mockDriver);

        ACCEPTER.accept(ALL_RECOMMENDATIONS);

        // Separate calls for each agent:
        verify(mockDriver, times(2)).acceptOffers(offerIdCaptor.capture(), operationCaptor.capture(), any());

        // Offer ids should be deduped within each agent. Also the ordering should be alphabetical based on agent id.
        List<Collection<Protos.OfferID>> offerIdCalls = offerIdCaptor.getAllValues();
        Assert.assertEquals(2, offerIdCalls.size());
        Assert.assertEquals(Collections.singleton(OFFER_A.getId()), offerIdCalls.get(0));
        Assert.assertEquals(Collections.singleton(OFFER_B.getId()), offerIdCalls.get(1));

        List<Collection<Protos.Offer.Operation>> operationCalls = operationCaptor.getAllValues();
        Assert.assertEquals(2, operationCalls.size());
        Assert.assertEquals(Arrays.asList(DESTROY_A.getOperation(), UNRESERVE_A.getOperation()), operationCalls.get(0));
        Assert.assertEquals(Arrays.asList(DESTROY_B.getOperation(), UNRESERVE_B.getOperation()), operationCalls.get(1));
    }
}
