package com.mesosphere.sdk.framework;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.TestStep;
import com.mesosphere.sdk.testutils.PodTestUtils;
import org.apache.mesos.SchedulerDriver;
import org.junit.AfterClass;
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
    private final UUID testUUID = UUID.randomUUID();
    @Mock private SchedulerDriver driver;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        Driver.setDriver(driver);
    }

    @AfterClass
    public static void afterAll() {
        // Reset to default behavior:
        ReviveManager.overrideTokenBucket(TokenBucket.newBuilder().build());
    }

    @Test
    public void reviveOnNewWork() {
        ReviveManager manager = getReviveManager();
        manager.revive(getSteps(0));
        verify(driver).reviveOffers();
    }

    @Test
    public void dontReviveOnTheSameWork() {
        ReviveManager manager = getReviveManager();
        manager.revive(getSteps(0));
        manager.revive(getSteps(0));
        verify(driver, times(1)).reviveOffers();
    }

    @Test
    public void reviveWhenTheSameWorkShowsUpLater() {
        ReviveManager manager = getReviveManager();
        manager.revive(getSteps(0));
        manager.revive(Collections.emptyList());
        manager.revive(getSteps(0));
        verify(driver, times(2)).reviveOffers();
    }

    @Test
    public void reviveOnAdditionalNewWork() {
        ReviveManager manager = getReviveManager();
        manager.revive(getSteps(0));
        manager.revive(getSteps(1));
        verify(driver, times(2)).reviveOffers();
    }

    @Test
    public void dontReviveWhenThrottled() {
        ReviveManager manager = new ReviveManager(Optional.empty());

        // Configure long delay. Should be inherited by previously constructed ReviveManager:
        ReviveManager.overrideTokenBucket(TokenBucket.newBuilder().acquireInterval(Duration.ofDays(1)).build());

        manager.revive(getSteps(0));
        manager.revive(getSteps(1));
        verify(driver, times(1)).reviveOffers();
    }

    @Test
    public void dontReviveAcrossManagers() {
        // Both managers should share the same underlying token bucket:
        ReviveManager a = new ReviveManager(Optional.empty());
        ReviveManager b = new ReviveManager(Optional.empty());

        // Configure long delay. Should be inherited by previously constructed ReviveManagers:
        ReviveManager.overrideTokenBucket(TokenBucket.newBuilder().acquireInterval(Duration.ofDays(1)).build());

        a.revive(getSteps(0)); // pass
        b.revive(getSteps(1)); // throttled
        verify(driver, times(1)).reviveOffers();
    }

    @Test
    public void dontReviveOnEmptyWork() {
        getReviveManager().revive(Collections.emptyList());
        verify(driver, times(0)).reviveOffers();
    }

    private static ReviveManager getReviveManager() {
        ReviveManager.overrideTokenBucket(TokenBucket.newBuilder().acquireInterval(Duration.ZERO).build());
        // Create a new ReviveManager, rather than reusing a single instance, so that the internal candidates are reset:
        return new ReviveManager(Optional.empty());
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
