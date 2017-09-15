package com.mesosphere.sdk.cassandra.scheduler;

import org.junit.Assert;
import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceTestBuilder;
import com.mesosphere.sdk.testing.ServiceTestResult;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        getTestBuilder().render();
    }

    @Test
    public void testSpecCustomUserAndSeeds() throws Exception {
        ServiceTestResult result = getTestBuilder()
                .setOptions("service.user", "foo")
                .setPodEnv("node", "REMOTE_SEEDS", "baz")
                .render();
        Assert.assertEquals("foo", result.getServiceSpec().getUser());
        Assert.assertEquals("foo", result.getServiceSpec().getPods().get(0).getUser().get());
        Assert.assertTrue(result.getTaskConfig("node", "server", "cassandra").contains("- seeds: \"foo,bar,baz\""));
    }

    @Test
    public void testSpecSSL() throws Exception {
        ServiceTestResult result = getTestBuilder()
                .setOptions("service.tls", "true")
                .render();
        Assert.assertTrue(result.getTaskConfig("node", "server", "cassandra").contains("internode_encryption: all"));
        Assert.assertTrue(result.getTaskConfig("node", "server", "cassandra").contains(
                "client_encryption_options:\n    enabled: true\n    optional: false"));
    }

    private ServiceTestBuilder getTestBuilder() {
        return new ServiceTestBuilder()
                .setPodEnv("node", "LOCAL_SEEDS", "foo,bar"); // would be provided by our Main.java
    }
}
