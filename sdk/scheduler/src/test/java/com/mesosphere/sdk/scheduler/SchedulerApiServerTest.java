package com.mesosphere.sdk.scheduler;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import java.time.Duration;
import java.util.Collections;

public class SchedulerApiServerTest {
    private static final int TIMEOUT_MILLIS = 100;
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void testApiServerTimeout() throws Exception {
        SchedulerApiServer schedulerApiServer = new SchedulerApiServer(12345, Collections.emptyList(),
                Duration.ofMillis(TIMEOUT_MILLIS));
        Assert.assertFalse(schedulerApiServer.ready());
        Thread.sleep(TIMEOUT_MILLIS * 10);
        exit.expectSystemExitWithStatus(SchedulerErrorCode.API_SERVER_TIMEOUT.getValue());
        schedulerApiServer.ready();
    }

    @Test
    public void testApiServerReady() throws Exception {
        SchedulerApiServer schedulerApiServer = new SchedulerApiServer(12345, Collections.emptyList(),
                Duration.ofMillis(TIMEOUT_MILLIS));
        new Thread(schedulerApiServer).start();
        Thread.sleep(TIMEOUT_MILLIS * 10);
        Assert.assertTrue(schedulerApiServer.ready());
    }

}