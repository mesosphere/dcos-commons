package com.mesosphere.sdk.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.mesosphere.sdk.scheduler.MesosEventClient.OfferResponse;
import com.mesosphere.sdk.scheduler.MesosEventClient.StatusResponse;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MultiMesosEventClientTest {

    private static final Answer<OfferResponse> DROP_FIRST_OFFER = new Answer<OfferResponse>() {
        @Override
        public OfferResponse answer(InvocationOnMock invocation) throws Throwable {
            List<Offer> offers = new ArrayList<>();
            offers.addAll(getOffersArgument(invocation));
            if (!offers.isEmpty()) {
                offers.remove(0);
            }
            return OfferResponse.processed(offers);
        }
    };

    private static final Answer<OfferResponse> DROP_LAST_OFFER = new Answer<OfferResponse>() {
        @Override
        public OfferResponse answer(InvocationOnMock invocation) throws Throwable {
            List<Offer> offers = new ArrayList<>();
            offers.addAll(getOffersArgument(invocation));
            if (!offers.isEmpty()) {
                offers.remove(offers.size() - 1);
            }
            return OfferResponse.processed(offers);
        }
    };

    private static final Answer<OfferResponse> OFFER_NOT_READY = new Answer<OfferResponse>() {
        @Override
        public OfferResponse answer(InvocationOnMock invocation) throws Throwable {
            return OfferResponse.notReady(getOffersArgument(invocation));
        }
    };

    @Mock private MesosEventClient mockClient1;
    @Mock private MesosEventClient mockClient2;
    @Mock private MesosEventClient mockClient3;

    private MultiMesosEventClient client;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        client = new MultiMesosEventClient();
    }

    @Test
    public void offerNoClients() {
        // Empty offers: All clients should have been pinged regardless
        OfferResponse response = client.offers(Collections.emptyList());
        Assert.assertEquals(OfferResponse.Result.NOT_READY, response.result);
        Assert.assertTrue(response.unusedOffers.isEmpty());

        // Seven offers: Only the middle offer is left at the end.
        List<Protos.Offer> offers = Arrays.asList(getOffer(1), getOffer(2), getOffer(3));
        response = client.offers(offers);
        Assert.assertEquals(OfferResponse.Result.NOT_READY, response.result);
        Assert.assertEquals(offers, response.unusedOffers);
    }

    @Test
    public void offerPruning() {
        // Client 1,4,7: returns all but the first offer
        // Client 2,5,8: returns all but the last offer
        // Client 3,6,9: not ready, no change to offers
        when(mockClient1.offers(any())).then(DROP_FIRST_OFFER);
        when(mockClient2.offers(any())).then(DROP_LAST_OFFER);
        when(mockClient3.offers(any())).then(OFFER_NOT_READY);
        client
                .addClient(mockClient1)
                .addClient(mockClient2)
                .addClient(mockClient3)
                .addClient(mockClient1)
                .addClient(mockClient2)
                .addClient(mockClient3)
                .addClient(mockClient1)
                .addClient(mockClient2)
                .addClient(mockClient3);

        // Empty offers: All clients should have been pinged regardless
        OfferResponse response = client.offers(Collections.emptyList());
        Assert.assertEquals(OfferResponse.Result.PROCESSED, response.result);
        Assert.assertTrue(response.unusedOffers.isEmpty());
        verify(mockClient1, times(3)).offers(Collections.emptyList());
        verify(mockClient2, times(3)).offers(Collections.emptyList());
        verify(mockClient3, times(3)).offers(Collections.emptyList());

        // Seven offers: Only the middle offer is left at the end.
        Protos.Offer middleOffer = getOffer(4);
        response = client.offers(Arrays.asList(
                getOffer(1), getOffer(2), getOffer(3),
                middleOffer,
                getOffer(5), getOffer(6), getOffer(7)));
        Assert.assertEquals(OfferResponse.Result.PROCESSED, response.result);
        Assert.assertEquals(1, response.unusedOffers.size());
        Assert.assertEquals(middleOffer, response.unusedOffers.get(0));
        verify(mockClient1, times(6)).offers(any());
        verify(mockClient2, times(6)).offers(any());
        verify(mockClient3, times(6)).offers(any());
    }

    @Test
    public void offerAllClientsNotReady() {
        // All three clients: Not ready
        when(mockClient1.offers(any())).then(OFFER_NOT_READY);
        client
                .addClient(mockClient1)
                .addClient(mockClient1)
                .addClient(mockClient1);

        // Empty offers: All clients should have been pinged regardless
        OfferResponse response = client.offers(Collections.emptyList());
        Assert.assertEquals(OfferResponse.Result.NOT_READY, response.result);
        Assert.assertTrue(response.unusedOffers.isEmpty());
        verify(mockClient1, times(3)).offers(Collections.emptyList());

        // Seven offers: Only the middle offer is left at the end.
        List<Protos.Offer> offers = Arrays.asList(getOffer(1), getOffer(2), getOffer(3));
        response = client.offers(offers);
        Assert.assertEquals(OfferResponse.Result.NOT_READY, response.result);
        Assert.assertEquals(offers, response.unusedOffers);
        verify(mockClient1, times(3)).offers(offers);
    }

    @Test
    public void statusAllUnknown() {
        // Client 1,2,3: unknown task
        when(mockClient1.status(any())).thenReturn(StatusResponse.unknownTask());
        client
                .addClient(mockClient1)
                .addClient(mockClient1)
                .addClient(mockClient1);

        Protos.TaskStatus status = getStatus();
        Assert.assertEquals(StatusResponse.Result.UNKNOWN_TASK, client.status(status).result);
        verify(mockClient1, times(3)).status(status);
    }
    @Test
    public void statusProcessed() {
        // Client 1,2,4: unknown task
        // Client 3: processed
        when(mockClient1.status(any())).thenReturn(StatusResponse.unknownTask());
        when(mockClient2.status(any())).thenReturn(StatusResponse.processed());
        client
                .addClient(mockClient1)
                .addClient(mockClient1)
                .addClient(mockClient2)
                .addClient(mockClient1);

        Protos.TaskStatus status = getStatus();
        Assert.assertEquals(StatusResponse.Result.PROCESSED, client.status(status).result);
        verify(mockClient1, times(2)).status(status); // stopped after hitting PROCESSED returned by mockClient2
        verify(mockClient2, times(1)).status(status);
    }

    private static Protos.TaskStatus getStatus() {
        return TaskTestUtils.generateStatus(
                Protos.TaskID.newBuilder().setValue("foo").build(),
                TaskState.TASK_FINISHED);
    }

    private static Protos.Offer getOffer(int id) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(Integer.toString(id)))
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Protos.Offer> getOffersArgument(InvocationOnMock invocation) {
        return (List<Protos.Offer>) invocation.getArguments()[0];
    }
}
