package com.mesosphere.sdk.cassandra.scheduler;

import org.junit.Assert;
import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceTestRunner;
import com.mesosphere.sdk.testing.SchedulerConfigResult;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        getTestBuilder().run();
    }

    @Test
    public void testSpecCustomUserAndSeeds() throws Exception {
        SchedulerConfigResult result = getTestBuilder()
                .setOptions("service.user", "foo")
                .setPodEnv("node", "REMOTE_SEEDS", "baz")
                .run();
        Assert.assertEquals("foo", result.getServiceSpec().getUser());
        Assert.assertEquals("foo", result.getServiceSpec().getPods().get(0).getUser().get());
        Assert.assertTrue(result.getTaskConfig("node", "server", "cassandra").contains("- seeds: \"foo,bar,baz\""));
    }

    @Test
    public void testSpecSSL() throws Exception {
        SchedulerConfigResult result = getTestBuilder()
                .setOptions("service.tls", "true")
                .run();
        Assert.assertTrue(result.getTaskConfig("node", "server", "cassandra").contains("internode_encryption: all"));
        Assert.assertTrue(result.getTaskConfig("node", "server", "cassandra").contains(
                "client_encryption_options:\n    enabled: true\n    optional: false"));
    }

    private ServiceTestRunner getTestBuilder() {
        return new ServiceTestRunner()
                .setPodEnv("node", "LOCAL_SEEDS", "foo,bar"); // would be provided by our Main.java
    }
}
