package com.mesosphere.sdk.queues.scheduler;

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

import com.mesosphere.sdk.scheduler.ServiceScheduler;
import com.mesosphere.sdk.scheduler.MesosEventClient.OfferResponse;
import com.mesosphere.sdk.scheduler.MesosEventClient.StatusResponse;

import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JobsEventClientTest {

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

    @Mock private ServiceScheduler mockClient1;
    @Mock private ServiceScheduler mockClient2;
    @Mock private ServiceScheduler mockClient3;

    private JobsEventClient client;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        client = new JobsEventClient();
    }

    @Test
    public void putRemoveClients() {
        Assert.assertNull(client.removeJob("1"));

        client.putJob("1", mockClient1);
        Assert.assertSame(mockClient1, client.removeJob("1"));
        Assert.assertNull(client.removeJob("1"));

        client.putJob("2", mockClient2);
        try {
            client.putJob("2", mockClient1);
            Assert.fail("Expected exception: duplicate key");
        } catch (IllegalArgumentException e) {
            // expected
        }
        Assert.assertNull(client.removeJob("1"));
        // Client should still have the original value that was successfully added:
        Assert.assertSame(mockClient2, client.removeJob("2"));
        Assert.assertNull(client.removeJob("2"));
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
                .putJob("1", mockClient1)
                .putJob("2", mockClient2)
                .putJob("3", mockClient3)
                .putJob("4", mockClient1)
                .putJob("5", mockClient2)
                .putJob("6", mockClient3)
                .putJob("7", mockClient1)
                .putJob("8", mockClient2)
                .putJob("9", mockClient3);

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
                .putJob("1", mockClient1)
                .putJob("2", mockClient1)
                .putJob("3", mockClient1);

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
                .putJob("1", mockClient1)
                .putJob("2", mockClient1)
                .putJob("3", mockClient1);

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
                .putJob("1", mockClient1)
                .putJob("2", mockClient1)
                .putJob("3", mockClient2)
                .putJob("4", mockClient1);

        Protos.TaskStatus status = getStatus();
        Assert.assertEquals(StatusResponse.Result.PROCESSED, client.status(status).result);
        verify(mockClient1, times(2)).status(status); // stopped after hitting PROCESSED returned by mockClient2
        verify(mockClient2, times(1)).status(status);
    }

    private static Protos.TaskStatus getStatus() {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue("foo").build())
                .setState(TaskState.TASK_FINISHED)
                .build();
    }

    private static Protos.Offer getOffer(int id) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(Integer.toString(id)))
                .setFrameworkId(Protos.FrameworkID.newBuilder().setValue("test-framework-id").build())
                .setSlaveId(Protos.SlaveID.newBuilder().setValue("test-slave-id").build())
                .setHostname("test-hostname")
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Protos.Offer> getOffersArgument(InvocationOnMock invocation) {
        return (List<Protos.Offer>) invocation.getArguments()[0];
    }
}
