package com.mesosphere.sdk.cassandra.scheduler;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceTestRunner;
import com.mesosphere.sdk.testing.ServiceTestResult;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        new ServiceTestRunner()
                .setPodEnv("node", getDefaultNodeEnv())
                .run();
    }

    @Test
    public void testSpecCustomUserAndSeeds() throws Exception {
        Map<String, String> nodeEnv = getDefaultNodeEnv();
        nodeEnv.put("REMOTE_SEEDS", "baz");
        ServiceTestResult result = new ServiceTestRunner()
                .setOptions("service.user", "foo")
                .setPodEnv("node", nodeEnv)
                .run();
        Assert.assertEquals("foo", result.getServiceSpec().getUser());
        Assert.assertEquals("foo", result.getServiceSpec().getPods().get(0).getUser().get());
        Assert.assertTrue(result.getTaskConfig("node", "server", "cassandra").contains("- seeds: \"foo,bar,baz\""));
    }

    @Test
    public void testSpecSSL() throws Exception {
        ServiceTestResult result = new ServiceTestRunner()
                .setOptions("service.security.transport_encryption.enabled", "true")
                .setPodEnv("node", getDefaultNodeEnv())
                .run();
        Assert.assertTrue(result.getTaskConfig("node", "server", "cassandra").contains("internode_encryption: all"));
        Assert.assertTrue(result.getTaskConfig("node", "server", "cassandra").contains(
                "client_encryption_options:\n    enabled: true\n    optional: false"));
    }

    private static Map<String, String> getDefaultNodeEnv() {
        Map<String, String> map = new HashMap<>();
        map.put("LOCAL_SEEDS", "foo,bar"); // would be provided by our Main.java
        return map;
    }
}
