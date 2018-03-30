package com.mesosphere.sdk.framework;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.TestConstants;

import static org.mockito.Mockito.*;

public class OfferProcessorTest {

    private static final Logger LOGGER = LoggingUtils.getLogger(OfferProcessorTest.class);
    private static final int THREAD_COUNT = 50;
    private static final int OFFERS_PER_THREAD = 3;

    @Mock private AbstractScheduler mockAbstractScheduler;
    @Mock private PlanCoordinator mockPlanCoordinator;
    @Mock private StateStore mockStateStore;
    @Mock private SchedulerDriver mockSchedulerDriver;

    private OfferProcessor processor;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        Driver.setDriver(mockSchedulerDriver);
        when(mockAbstractScheduler.getPlanCoordinator()).thenReturn(mockPlanCoordinator);

        processor = new OfferProcessor(mockAbstractScheduler, mockStateStore);
    }

    @Test
    public void testDeclineCall() {
        final List<Protos.Offer> offers = Arrays.asList(getOffer());
        final List<Protos.OfferID> offerIds = offers.stream().map(Protos.Offer::getId).collect(Collectors.toList());
        Driver.setDriver(mockSchedulerDriver);
        OfferProcessor.declineShort(offers);
        verify(mockSchedulerDriver).declineOffer(eq(offerIds.get(0)), any());
    }

    @Test
    public void testAsyncOffersLimitedQueueSize() throws InterruptedException {
        processor.setOfferQueueSize(10).start();

        // At least some offers should have been dropped/declined before reaching the client:
        Set<String> sentOfferIds = sendOffers(THREAD_COUNT, OFFERS_PER_THREAD);
        verify(mockAbstractScheduler, atLeastOnce()).processOffers(any(), any());
        verify(mockAbstractScheduler, atMost(sentOfferIds.size() - 1)).processOffers(any(), any());
        verify(mockSchedulerDriver, atLeastOnce()).declineOffer(any(), any());
    }

    @Test
    public void testAsyncOffersUnlimitedQueueSize() throws InterruptedException {
        // The queueing results in accumulating all the offer lists into a flat list.
        // So we need to explicitly collect an offer count.
        AtomicInteger receivedCount = new AtomicInteger(0);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                List<Protos.Offer> offers = getOffersArgument(invocation);
                receivedCount.addAndGet(offers.size());
                return null;
            }
        }).when(mockAbstractScheduler).processOffers(any(), any());

        processor.setOfferQueueSize(0).start(); // unlimited queue size

        // No offers should have been dropped/declined:
        Set<String> sentOfferIds = sendOffers(THREAD_COUNT, OFFERS_PER_THREAD);
        Assert.assertEquals(receivedCount.get(), sentOfferIds.size());
        verify(mockSchedulerDriver, never()).declineOffer(any(), any());
    }

    private Set<String> sendOffers(int threadCount, int offersPerThread) throws InterruptedException {
        // Hammer scheduler with offers, and check that they were all forwarded as expected
        Set<String> sentOfferIds = new HashSet<>();
        List<Thread> threads = new ArrayList<>();
        for (int iThread = 0; iThread < threadCount; ++iThread) {
            List<Protos.Offer> offers = new ArrayList<>();
            for (int iOffer = 0; iOffer < offersPerThread; ++iOffer) {
                offers.add(getOffer());
            }
            sentOfferIds.addAll(offers.stream()
                    .map(o -> o.getId().getValue())
                    .collect(Collectors.toList()));

            final String threadName = String.format("offer-%d", iThread);
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    LOGGER.info("Thread {} sending {} offers...", threadName, offers.size());
                    processor.enqueue(offers);
                }
            }, threadName);
            threads.add(t);
            t.start();
        }

        LOGGER.info("Created {} threads.", threadCount);

        // Wait for input to finish:
        for (Thread t : threads) {
            LOGGER.info("Waiting on thread {}...", t.getName());
            t.join();
            LOGGER.info("Thread {} has exited", t.getName());
        }

        processor.awaitOffersProcessed();

        return sentOfferIds;
    }

    private static Protos.Offer getOffer() {
        return getOffer(UUID.randomUUID().toString());
    }

    private static Protos.Offer getOffer(String id) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(id))
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .addResources(getUnreservedCpus(3))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Protos.Offer> getOffersArgument(InvocationOnMock invocation) {
        return (List<Protos.Offer>) invocation.getArguments()[0];
    }

    @SuppressWarnings("deprecation")
    private static Protos.Resource getUnreservedCpus(double cpus) {
        Protos.Resource.Builder resBuilder = Protos.Resource.newBuilder()
                .setName("cpus")
                .setType(Protos.Value.Type.SCALAR)
                .setRole(Constants.ANY_ROLE);
        resBuilder.getScalarBuilder().setValue(cpus);
        return resBuilder.build();
    }
}
