package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.PodTestUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/**
 * This class tests {@link ReviveManager}.
 */
public class ReviveManagerTest {
    private StateStore stateStore;
    private ReviveManager manager;
    @Mock private PlanCoordinator planCoordinator;
    @Mock SchedulerDriver driver;


    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        stateStore = new StateStore(new MemPersister());
        manager = null;
    }

    @Test(expected = ConditionTimeoutException.class)
    public void stayRevivedWhenWorkIsIncomplete() {
        when(planCoordinator.getCandidates()).thenReturn(Arrays.asList(getStep(0)));
        Assert.assertFalse(StateStoreUtils.isSuppressed(stateStore));
        manager = getSuppressReviveManager(planCoordinator);
        waitSuppressed(stateStore, manager, 5);
    }

    @Test
    public void reviveAgainWhenNewWorkAppears() {
        when(planCoordinator.getCandidates()).thenReturn(Arrays.asList(getStep(0)));
        Assert.assertFalse(StateStoreUtils.isSuppressed(stateStore));
        manager = getSuppressReviveManager(planCoordinator);

        // The PlanCoordinator returns new work.
        when(planCoordinator.getCandidates()).thenReturn(Arrays.asList(getStep(1)));

        verify(driver, timeout(5000).atLeastOnce()).reviveOffers();
    }

    private ReviveManager getSuppressReviveManager(PlanCoordinator planCoordinator) {
        return new ReviveManager(
                driver,
                stateStore,
                planCoordinator,
                0,
                1);
    }

    private Step getStep(int index) {
        PodInstanceRequirement podInstanceRequirement = PodTestUtils.getPodInstanceRequirement(index);
        UUID id = UUID.randomUUID();

        return new Step() {
            @Override
            public Optional<PodInstanceRequirement> start() {
                return getPodInstanceRequirement();
            }

            @Override
            public Optional<PodInstanceRequirement> getPodInstanceRequirement() {
                return Optional.of(podInstanceRequirement);
            }

            @Override
            public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
                // Intentionally empty
            }

            @Override
            public Optional<PodInstanceRequirement> getAsset() {
                return getPodInstanceRequirement();
            }

            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getName() {
                return String.format("step-%d", podInstanceRequirement.getPodInstance().getIndex());
            }

            @Override
            public Status getStatus() {
                return Status.PENDING;
            }

            @Override
            public void update(Protos.TaskStatus status) {
                // Intentionally empty
            }

            @Override
            public void restart() {
                // Intentionally empty
            }

            @Override
            public void forceComplete() {
                // Intentionally empty
            }

            @Override
            public List<String> getErrors() {
                return Collections.emptyList();
            }

            @Override
            public void interrupt() {
                // Intentionally empty
            }

            @Override
            public void proceed() {
                // Intentionally empty
            }

            @Override
            public boolean isInterrupted() {
                return false;
            }
        };
    }

    private static void waitSuppressed(StateStore stateStore, ReviveManager reviveManager, int seconds) {
        waitStateStore(stateStore, true, seconds);
    }

    private static void waitRevived(StateStore stateStore, ReviveManager reviveManager, int seconds) {
        waitStateStore(stateStore, false, seconds);
    }

    private static void waitStateStore(StateStore stateStore, boolean suppressed, int seconds) {
        Awaitility.await()
                .atMost(seconds, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return StateStoreUtils.isSuppressed(stateStore) == suppressed;
                    }
                });
    }
}
