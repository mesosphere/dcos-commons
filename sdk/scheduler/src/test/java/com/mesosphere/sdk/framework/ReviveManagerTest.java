package com.mesosphere.sdk.framework;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.TestStep;
import com.mesosphere.sdk.testutils.PodTestUtils;
import org.apache.mesos.SchedulerDriver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * This class tests {@link ReviveManager}.
 */
public class ReviveManagerTest {
    private ReviveManager manager;
    private final UUID testUUID = UUID.randomUUID();
    @Mock private SchedulerDriver driver;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        Driver.setDriver(driver);
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
    public void dontReviveWhenThrottled() {
        ReviveManager manager = getReviveManager(Duration.ofDays(1));
        manager.revive(getSteps(0));
        manager.revive(getSteps(1));
        verify(driver, times(1)).reviveOffers();
    }

    @Test
    public void dontReviveSharedTokenBucket() {
        // Default constructor should use a global token bucket.
        ReviveManager.resetTimers();
        ReviveManager a = new ReviveManager(Optional.empty());
        ReviveManager b = new ReviveManager(Optional.empty());
        a.revive(getSteps(0)); // pass
        b.revive(getSteps(1)); // throttled
        verify(driver, times(1)).reviveOffers();
    }

    @Test
    public void dontReviveOnEmptyWork() {
        manager = getReviveManager();
        manager.revive(Collections.emptyList());
        verify(driver, times(0)).reviveOffers();
    }

    private ReviveManager getReviveManager() {
        return getReviveManager(Duration.ZERO);
    }

    private ReviveManager getReviveManager(Duration duration) {
        return new ReviveManager(TokenBucket.newBuilder().acquireInterval(duration).build(), Optional.empty());
    }

    private List<Step> getSteps(Integer index) {
        PodInstanceRequirement podInstanceRequirement = PodTestUtils.getPodInstanceRequirement(index);
        return Arrays.asList(
                new TestStep(
                        testUUID,
                        String.format("step-%d", podInstanceRequirement.getPodInstance().getIndex()),
                        podInstanceRequirement));
    }
}
