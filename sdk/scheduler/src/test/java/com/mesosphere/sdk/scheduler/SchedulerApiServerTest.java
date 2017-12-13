package com.mesosphere.sdk.scheduler;

import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchedulerApiServerTest {
    private static final int SHORT_TIMEOUT_MILLIS = 100;
    private static final int LONG_TIMEOUT_MILLIS = 30000;

    @Test
    public void testApiServerReady() throws Exception {
        SchedulerApiServer schedulerApiServer = new SchedulerApiServer(
                getSchedulerConfig(0, Duration.ofMillis(LONG_TIMEOUT_MILLIS)), Collections.emptyList());
        Listener listener = new Listener();
        schedulerApiServer.start(listener);
        waitForTrue(listener.apiServerStarted);
    }

    private SchedulerConfig getSchedulerConfig(int port, Duration timeout) {
        SchedulerConfig mockSchedulerConfig = mock(SchedulerConfig.class);
        when(mockSchedulerConfig.getApiServerInitTimeout()).thenReturn(timeout);
        when(mockSchedulerConfig.getApiServerPort()).thenReturn(port);
        return mockSchedulerConfig;
    }

    private static void waitForTrue(AtomicBoolean bool) throws InterruptedException {
        int maxSleepCount = LONG_TIMEOUT_MILLIS / SHORT_TIMEOUT_MILLIS;
        for (int i = 0; i < maxSleepCount && !bool.get(); ++i) {
            Thread.sleep(SHORT_TIMEOUT_MILLIS);
        }
        Assert.assertTrue(bool.get());
    }

    private static class Listener extends AbstractLifeCycle.AbstractLifeCycleListener {

        private final AtomicBoolean apiServerStarted = new AtomicBoolean(false);

        @Override
        public void lifeCycleStarted(LifeCycle event) {
            apiServerStarted.set(true);
        }
    }
}
