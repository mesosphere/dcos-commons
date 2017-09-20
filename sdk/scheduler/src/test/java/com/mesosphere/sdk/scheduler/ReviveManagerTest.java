package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.TestStep;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.PodTestUtils;
import org.apache.mesos.SchedulerDriver;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/**
 * This class tests {@link ReviveManager}.
 */
public class ReviveManagerTest {
    private StateStore stateStore;
    private ReviveManager manager;
    private final UUID testUUID = UUID.randomUUID();
    @Mock private SchedulerDriver driver;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        stateStore = new StateStore(new MemPersister());
        manager = null;
    }

    @Test
    public void reviveOnNewWork() {
        manager = getReviveManager();
        manager.revive(getSteps(0));
        verify(driver).reviveOffers();
    }

    @Test
    public void dontReviveOnTheSameWork() {
        manager = getReviveManager();
        manager.revive(getSteps(0));
        manager.revive(getSteps(0));
        verify(driver, times(1)).reviveOffers();
    }

    @Test
    public void reviveWhenTheSameWorkShowsUpLater() {
        manager = getReviveManager();
        manager.revive(getSteps(0));
        manager.revive(Collections.emptyList());
        manager.revive(getSteps(0));
        verify(driver, times(2)).reviveOffers();
    }

    @Test
    public void reviveOnAdditionalNewWork() {
        manager = getReviveManager();
        manager.revive(getSteps(0));
        manager.revive(getSteps(1));
        verify(driver, times(2)).reviveOffers();
    }

    @Test
    public void dontReviveOnEmptyWork() {
        manager = getReviveManager();
        manager.revive(Collections.emptyList());
        verify(driver, times(0)).reviveOffers();
    }

    private ReviveManager getReviveManager() {
        return new ReviveManager(driver, stateStore);
    }

    private List<Step> getSteps(Integer index) {
        PodInstanceRequirement podInstanceRequirement = PodTestUtils.getPodInstanceRequirement(index);
        return Arrays.asList(
                new TestStep(
                        testUUID,
                        String.format("step-%d", podInstanceRequirement.getPodInstance().getIndex()),
                        podInstanceRequirement));
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
