package com.mesosphere.sdk.scheduler;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import java.time.Duration;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchedulerApiServerTest {
    private static final int TIMEOUT_MILLIS = 100;
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void testApiServerTimeout() throws Exception {
        SchedulerApiServer schedulerApiServer =
                new SchedulerApiServer(getFlags(0, Duration.ofMillis(TIMEOUT_MILLIS)), Collections.emptyList());
        Assert.assertFalse(schedulerApiServer.ready());
        schedulerApiServer.start();
        Thread.sleep(TIMEOUT_MILLIS * 10);
        exit.expectSystemExitWithStatus(SchedulerErrorCode.API_SERVER_TIMEOUT.getValue());
        schedulerApiServer.ready();
    }

    @Test
    public void testApiServerReady() throws Exception {
        SchedulerApiServer schedulerApiServer =
                new SchedulerApiServer(getFlags(0, Duration.ofSeconds(30)), Collections.emptyList());
        schedulerApiServer.start();
        while (!schedulerApiServer.ready()) {
            Thread.sleep(TIMEOUT_MILLIS);
        }
        Assert.assertTrue(schedulerApiServer.ready());
    }

    private SchedulerFlags getFlags(int port, Duration timeout) {
        SchedulerFlags mockFlags = mock(SchedulerFlags.class);
        when(mockFlags.getApiServerInitTimeout()).thenReturn(timeout);
        when(mockFlags.getApiServerPort()).thenReturn(port);
        return mockFlags;
    }
}