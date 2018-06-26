package com.mesosphere.sdk.framework;

import org.apache.mesos.SchedulerDriver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.scheduler.SchedulerConfig;

import java.time.Duration;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link ReviveManager}
 */
public class ReviveManagerTest {

    @Mock private SchedulerDriver driver;
    @Mock private SchedulerConfig mockSchedulerConfig;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        Driver.setDriver(driver);
        when(mockSchedulerConfig.isSuppressEnabled()).thenReturn(true);
    }

    @Test
    public void dontReviveWhenNotRequested() {
        ReviveManager manager = getReviveManager();

        manager.reviveIfRequested();

        verify(driver, times(0)).reviveOffers();
    }

    @Test
    public void dontReviveWhenThrottled() {
        ReviveManager manager = getReviveManager();

        manager.requestRevive();
        manager.reviveIfRequested();
        manager.requestRevive();
        manager.reviveIfRequested();

        verify(driver, times(1)).reviveOffers();
    }

    @Test
    public void dontReviveAcrossManagers() {
        // Both managers should share the same underlying token bucket:
        TokenBucket tokenBucket = TokenBucket.newBuilder().acquireInterval(Duration.ofDays(1)).build();
        ReviveManager a = new ReviveManager(tokenBucket, mockSchedulerConfig);
        ReviveManager b = new ReviveManager(tokenBucket, mockSchedulerConfig);

        a.requestRevive();
        b.requestRevive();
        a.reviveIfRequested(); // pass
        b.reviveIfRequested(); // throttled

        verify(driver, times(1)).reviveOffers();
    }

    @Test
    public void suppressRevive() {
        ReviveManager manager = new ReviveManager(
                TokenBucket.newBuilder().acquireInterval(Duration.ZERO).build(), mockSchedulerConfig);

        // Suppress:
        manager.suppressIfActive();
        verify(driver, times(1)).suppressOffers();

        // Revive:
        manager.requestReviveIfSuppressed();
        manager.reviveIfRequested();
        verify(driver, times(1)).reviveOffers();

        // Revive again, because previous revive apparently didn't go through:
        manager.requestReviveIfSuppressed();
        manager.reviveIfRequested();
        verify(driver, times(2)).reviveOffers();

        // Finally get an offer, un-suppress must've worked.
        manager.notifyOffersReceived();

        // Now that we aren't suppressed, revive is not triggered by just needing offers:
        manager.requestReviveIfSuppressed();
        manager.reviveIfRequested();
        verify(driver, times(2)).reviveOffers();

        // .. but still revives if specifically requested (due to new work):
        manager.requestRevive();
        manager.reviveIfRequested();
        verify(driver, times(3)).reviveOffers();
    }

    @Test
    public void reSuppressWhenSuppressed() {
        ReviveManager manager = getReviveManager();
        manager.suppressIfActive();
        manager.suppressIfActive();
        verify(driver, times(2)).suppressOffers();
    }

    @Test
    public void dontSuppressWhenDisabled() {
        when(mockSchedulerConfig.isSuppressEnabled()).thenReturn(false);
        ReviveManager manager = getReviveManager();
        manager.suppressIfActive();
        verify(driver, never()).suppressOffers();
    }

    private ReviveManager getReviveManager() {
        return new ReviveManager(
                TokenBucket.newBuilder().acquireInterval(Duration.ofDays(1)).build(), mockSchedulerConfig);
    }
}
