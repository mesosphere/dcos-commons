package com.mesosphere.sdk.scheduler;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

public class SchedulerConfigTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testUninstallIsEnabled() throws Exception {
        environmentVariables.set("SDK_UNINSTALL", "true");
        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
        Assert.assertTrue(schedulerConfig.isUninstallEnabled());
        environmentVariables.set("SDK_UNINSTALL", "can be set to anything");
        schedulerConfig = SchedulerConfig.fromEnv();
        Assert.assertTrue(schedulerConfig.isUninstallEnabled());
    }

    @Test
    public void testUninstallIsDisabled() throws Exception {
        environmentVariables.set("SDK_UNINSTALL", null);
        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
        Assert.assertFalse(schedulerConfig.isUninstallEnabled());
    }
}