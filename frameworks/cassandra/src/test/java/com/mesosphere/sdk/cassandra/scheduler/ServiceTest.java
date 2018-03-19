package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.testing.ConfigValidatorUtils;
import com.mesosphere.sdk.testing.ServiceTestResult;
import com.mesosphere.sdk.testing.ServiceTestRunner;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class ServiceTest {
    private static final ConfigValidator<ServiceSpec> validator = new CassandraZoneValidator();

    @Test
    public void testSpec() throws Exception {
        new ServiceTestRunner().setPodEnv("node", getDefaultNodeEnv()).run();
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

    @Test
    public void rejectRackEnablement() throws Exception {
        ConfigValidatorUtils.rejectRackEnablement(validator, getZoneRunner(), "PLACEMENT_CONSTRAINT");
    }

    @Test
    public void rejectRackDisablement() throws Exception {
        ConfigValidatorUtils.rejectRackDisablement(validator, getZoneRunner(), "PLACEMENT_CONSTRAINT");
    }

    @Test
    public void allowRackChanges() throws Exception {
        ConfigValidatorUtils.allowRackChanges(validator, getZoneRunner(), "PLACEMENT_CONSTRAINT");
    }

    @Test
    public void testRegionAwareness() throws Exception {
        ServiceTestResult result = new ServiceTestRunner()
                .setOptions("service.region", "Europe")
                .setPodEnv("node", getDefaultNodeEnv())
                .run();
        Assert.assertEquals(result.getSchedulerEnvironment().get("SERVICE_REGION"), "Europe");
        Assert.assertEquals(result.getSchedulerEnvironment().get("ALLOW_REGION_AWARENESS"), "true");
    }

    private static Map<String, String> getDefaultNodeEnv() {
        Map<String, String> map = new HashMap<>();
        map.put("LOCAL_SEEDS", "foo,bar"); // would be provided by our Main.java
        return map;
    }

    private ServiceTestRunner getZoneRunner() {
        Map<String, String> map = getDefaultNodeEnv();
        map.put("ZONE", "zone");
        return new ServiceTestRunner().setPodEnv("node", map);
    }
}
