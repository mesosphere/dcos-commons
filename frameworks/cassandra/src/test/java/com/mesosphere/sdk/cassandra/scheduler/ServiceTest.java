package com.mesosphere.sdk.cassandra.scheduler;

import org.junit.Assert;
import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceTestBuilder;
import com.mesosphere.sdk.testing.ServiceTestResult;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        new ServiceTestBuilder()
                .setPodEnv("node", "LOCAL_SEEDS", "foo,bar") // would be provided by our Main.java
                .render();
    }

    @Test
    public void testSpecCustomUserAndSeeds() throws Exception {
        ServiceTestResult result = new ServiceTestBuilder()
                .setOptions("service.user", "foo")
                .setPodEnv("node", "LOCAL_SEEDS", "foo,bar")
                .setPodEnv("node", "REMOTE_SEEDS", "baz")
                .render();
        Assert.assertEquals("foo", result.getServiceSpec().getUser());
        Assert.assertEquals("foo", result.getServiceSpec().getPods().get(0).getUser().get());
        Assert.assertTrue(result.getTaskConfig("node", "server", "cassandra").contains("- seeds: \"foo,bar,baz\""));
    }
}
