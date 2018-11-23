package com.mesosphere.sdk.specification;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.mesosphere.sdk.config.SerializationUtils;

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

    @Test
    public void upgradeFromOldHealthCheckSpec() throws IOException {
        // Old version with just gracePeriod
        String oldHealthCheckSpecJson = "{\n" +
                "  \"command\" : \"some-command\",\n" +
                "  \"max-consecutive-failures\" : 4,\n" +
                "  \"delay\" : 0,\n" +
                "  \"interval\" : 15,\n" +
                "  \"timeout\" : 10,\n" +
                "  \"gracePeriod\" : 120\n" +
                "}";
        DefaultHealthCheckSpec healthCheck =
                SerializationUtils.fromJsonString(oldHealthCheckSpecJson, DefaultHealthCheckSpec.class);

        Assert.assertEquals((Integer) 120, healthCheck.getGracePeriod());
        Assert.assertEquals((Integer) 120, healthCheck.getGracePeriodForDowngradeCompatibility());

        String expectedOutputJson = "{\n" +
                "  \"command\" : \"some-command\",\n" +
                "  \"max-consecutive-failures\" : 4,\n" +
                "  \"delay\" : 0,\n" +
                "  \"interval\" : 15,\n" +
                "  \"timeout\" : 10,\n" +
                "  \"grace-period\" : 120,\n" + // Expect both new and old versions in output for downgrade compatibility
                "  \"gracePeriod\" : 120\n" +
                "}";
        Assert.assertEquals(expectedOutputJson, SerializationUtils.toJsonString(healthCheck));
    }

    @Test
    public void parseNewDualHealthCheckSpec() throws IOException {
        // New version with both grace-period and gracePeriod: grace-period takes precedent
        String newHealthCheckSpecJson = "{\n" +
                "  \"command\" : \"some-command\",\n" +
                "  \"max-consecutive-failures\" : 4,\n" +
                "  \"delay\" : 0,\n" +
                "  \"interval\" : 15,\n" +
                "  \"timeout\" : 10,\n" +
                "  \"grace-period\" : 120,\n" +
                "  \"gracePeriod\" : 130\n" +
                "}";
        DefaultHealthCheckSpec healthCheck =
                SerializationUtils.fromJsonString(newHealthCheckSpecJson, DefaultHealthCheckSpec.class);

        // gracePeriod is ignored since grace-period is present:
        Assert.assertEquals((Integer) 120, healthCheck.getGracePeriod());
        Assert.assertEquals((Integer) 120, healthCheck.getGracePeriodForDowngradeCompatibility());

        String expectedOutputJson = "{\n" +
                "  \"command\" : \"some-command\",\n" +
                "  \"max-consecutive-failures\" : 4,\n" +
                "  \"delay\" : 0,\n" +
                "  \"interval\" : 15,\n" +
                "  \"timeout\" : 10,\n" +
                "  \"grace-period\" : 120,\n" + // Expect both new and old versions in output for downgrade compatibility
                "  \"gracePeriod\" : 120\n" + // Expect old camelcase value to get updated as well
                "}";
        Assert.assertEquals(expectedOutputJson, SerializationUtils.toJsonString(healthCheck));
    }

    @Test
    public void parseFutureSingleHealthCheckSpec() throws IOException {
        // Future version with just grace-period:
        String newHealthCheckSpecJson = "{\n" +
                "  \"command\" : \"some-command\",\n" +
                "  \"max-consecutive-failures\" : 4,\n" +
                "  \"delay\" : 0,\n" +
                "  \"interval\" : 15,\n" +
                "  \"timeout\" : 10,\n" +
                "  \"grace-period\" : 120\n" +
                "}";
        DefaultHealthCheckSpec healthCheck =
                SerializationUtils.fromJsonString(newHealthCheckSpecJson, DefaultHealthCheckSpec.class);

        // Old camelcase value is ignored since new value is non-null:
        Assert.assertEquals((Integer) 120, healthCheck.getGracePeriod());
        Assert.assertEquals((Integer) 120, healthCheck.getGracePeriodForDowngradeCompatibility());

        String expectedOutputJson = "{\n" +
                "  \"command\" : \"some-command\",\n" +
                "  \"max-consecutive-failures\" : 4,\n" +
                "  \"delay\" : 0,\n" +
                "  \"interval\" : 15,\n" +
                "  \"timeout\" : 10,\n" +
                "  \"grace-period\" : 120,\n" + // Expect both new and old versions in output for downgrade compatibility
                "  \"gracePeriod\" : 120\n" +
                "}";
        Assert.assertEquals(expectedOutputJson, SerializationUtils.toJsonString(healthCheck));
    }
}
