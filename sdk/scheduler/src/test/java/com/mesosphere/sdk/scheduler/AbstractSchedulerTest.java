package com.mesosphere.sdk.scheduler;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;

import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

/**
 * Tests for {@link AbstractScheduler}.
 */
public class AbstractSchedulerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSchedulerTest.class);

    private StateStore stateStore;

    @Mock private ConfigStore<ServiceSpec> mockConfigStore;
    @Mock private SchedulerDriver mockSchedulerDriver;
    @Mock private SecretsClient mockSecretsClient;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        stateStore = new StateStore(new MemPersister());
    }

    @Test
    public void testApiServerNotReadyDecline() throws PersisterException {
        // Build a new scheduler where the API server wasn't disabled:
        AbstractScheduler scheduler = getScheduler(true, false, -1);
        scheduler.getMesosScheduler().get()
                .resourceOffers(mockSchedulerDriver, Arrays.asList(getOffer(), getOffer(), getOffer()));
        verify(mockSchedulerDriver, times(3)).declineOffer(any(), any());
    }

    @Test
    public void testAsyncOffersLimitedQueueSize() throws PersisterException, InterruptedException {
        TestScheduler scheduler = getScheduler(false, true, 10);

        final int threadCount = 50;
        final int offersPerThread = 3;

        // At least some offers should have been dropped:
        Set<String> sentOfferIds = sendOffers(scheduler, threadCount, offersPerThread);
        Assert.assertTrue(String.format("sent %d, got %d", sentOfferIds.size(), scheduler.receivedOfferIds.size()),
                sentOfferIds.size() > scheduler.receivedOfferIds.size());
        verify(mockSchedulerDriver, atLeastOnce()).declineOffer(any(), any());
    }

    @Test
    public void testAsyncOffersUnlimitedQueueSize() throws PersisterException, InterruptedException {
        TestScheduler scheduler = getScheduler(false, true, 0);

        final int threadCount = 50;
        final int offersPerThread = 3;

        // No offers should have been dropped:
        Set<String> sentOfferIds = sendOffers(scheduler, threadCount, offersPerThread);
        Assert.assertEquals(String.format("sent %d, got %d", sentOfferIds.size(), scheduler.receivedOfferIds.size()),
                sentOfferIds, scheduler.receivedOfferIds);
        verify(mockSchedulerDriver, never()).declineOffer(any(), any());
    }

    private Set<String> sendOffers(AbstractScheduler scheduler, int threadCount, int offersPerThread)
            throws InterruptedException {
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
                    scheduler.getMesosScheduler().get().resourceOffers(mockSchedulerDriver, offers);
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

        // Wait for internal processing to finish before returning:
        scheduler.awaitOffersProcessed();

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
                .build();
    }

    private TestScheduler getScheduler(boolean waitForApiServer, boolean multithreaded, int offerQueueSize)
            throws PersisterException {
        TestScheduler scheduler = new TestScheduler(
                stateStore, mockConfigStore, SchedulerConfigTestUtils.getTestSchedulerConfig());
        // Customize...
        if (!waitForApiServer) {
            scheduler.disableApiServer();
        }
        if (!multithreaded) {
            scheduler.disableThreading();
        }
        if (offerQueueSize >= 0) {
            scheduler.setOfferQueueSize(offerQueueSize);
        }
        // Start and register.
        scheduler.start();
        scheduler.getMesosScheduler().get()
                .registered(mockSchedulerDriver, TestConstants.FRAMEWORK_ID, TestConstants.MASTER_INFO);
        return scheduler;
    }

    private static class TestScheduler extends AbstractScheduler {

        private final PlanCoordinator mockPlanCoordinator = mock(PlanCoordinator.class);

        private final Set<String> receivedOfferIds = new HashSet<>();

        protected TestScheduler(
                StateStore stateStore, ConfigStore<ServiceSpec> configStore, SchedulerConfig schedulerConfig) {
            super(stateStore, configStore, schedulerConfig);
            when(mockPlanCoordinator.getPlanManagers()).thenReturn(Collections.emptyList());
            when(mockPlanCoordinator.getCandidates()).thenReturn(Collections.emptyList());
        }

        @Override
        protected Collection<Object> getResources() {
            return Collections.emptyList();
        }

        @Override
        protected PlanCoordinator initialize(SchedulerDriver driver) throws Exception {
            return mockPlanCoordinator;
        }

        @Override
        protected void processOffers(SchedulerDriver driver, List<Protos.Offer> offers, Collection<Step> steps) {
            receivedOfferIds.addAll(offers.stream()
                    .map(o -> o.getId().getValue())
                    .collect(Collectors.toList()));
        }

        @Override
        protected void processStatusUpdate(Protos.TaskStatus status) throws Exception {
            throw new UnsupportedOperationException();
        }
    }
}
