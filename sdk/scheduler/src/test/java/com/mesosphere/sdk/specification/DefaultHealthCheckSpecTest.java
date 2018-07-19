package com.mesosphere.sdk.specification;

import org.junit.Assert;
import org.junit.Test;

public class DefaultHealthCheckSpecTest {

    @Test
    public void validHealthCheckSpec() {
        DefaultHealthCheckSpec healthCheckSpec = DefaultHealthCheckSpec.newBuilder()
                .command("echo true")
                .delay(0)
                .gracePeriod(0)
                .interval(0)
                .maxConsecutiveFailures(1)
                .timeout(0)
                .build();

        Assert.assertNotNull(healthCheckSpec);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidHealthCheckSpec() {
        DefaultHealthCheckSpec.newBuilder()
                .command("")
                .delay(-1)
                .gracePeriod(-1)
                .interval(-1)
                .maxConsecutiveFailures(-1)
                .timeout(-1)
                .build();
    }
}
