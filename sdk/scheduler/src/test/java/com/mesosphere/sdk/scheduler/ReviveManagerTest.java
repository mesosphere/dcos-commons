package com.mesosphere.sdk.scheduler;

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
        ReviveManager manager = new ReviveManager(driver);
        manager.revive(getSteps(0));
        manager.revive(getSteps(1));
        verify(driver, times(1)).reviveOffers();
    }

    @Test
    public void dontReviveOnEmptyWork() {
        manager = getReviveManager();
        manager.revive(Collections.emptyList());
        verify(driver, times(0)).reviveOffers();
    }

    private ReviveManager getReviveManager() {
        return new ReviveManager(driver, TokenBucket.newBuilder().acquireInterval(Duration.ZERO).build());
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
