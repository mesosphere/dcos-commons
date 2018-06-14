package com.mesosphere.sdk.framework;

import org.apache.mesos.SchedulerDriver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link ReviveManager}
 */
public class ReviveManagerTest {

    @Mock private SchedulerDriver driver;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        Driver.setDriver(driver);
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
        ReviveManager a = new ReviveManager(tokenBucket);
        ReviveManager b = new ReviveManager(tokenBucket);

        a.requestRevive();
        b.requestRevive();
        a.reviveIfRequested(); // pass
        b.reviveIfRequested(); // throttled

        verify(driver, times(1)).reviveOffers();
    }

    @Test
    public void suppressRevive() {
        ReviveManager manager = new ReviveManager(TokenBucket.newBuilder().acquireInterval(Duration.ZERO).build());

        // Suppress:
        manager.notifyOffersNeeded(false);
        verify(driver, times(1)).suppressOffers();

        // Revive:
        manager.notifyOffersNeeded(true);
        manager.reviveIfRequested();
        verify(driver, times(1)).reviveOffers();

        // Revive again, because previous revive apparently didn't go through:
        manager.notifyOffersNeeded(true);
        manager.reviveIfRequested();
        verify(driver, times(2)).reviveOffers();

        // Finally get an offer, un-suppress must've worked.
        manager.notifyOffersReceived();

        // Now that we aren't suppressed, revive is not triggered by just needing offers:
        manager.notifyOffersNeeded(true);
        manager.reviveIfRequested();
        verify(driver, times(2)).reviveOffers();

        // .. but still revives if specifically requested (due to new work):
        manager.requestRevive();
        manager.reviveIfRequested();
        verify(driver, times(3)).reviveOffers();
    }

    @Test
    public void dontSuppressWhenSuppressed() {
        ReviveManager manager = getReviveManager();
        manager.notifyOffersNeeded(false);
        manager.notifyOffersNeeded(false);
        verify(driver, times(1)).suppressOffers();
    }

    private static ReviveManager getReviveManager() {
        return new ReviveManager(TokenBucket.newBuilder().acquireInterval(Duration.ofDays(1)).build());
    }
}
