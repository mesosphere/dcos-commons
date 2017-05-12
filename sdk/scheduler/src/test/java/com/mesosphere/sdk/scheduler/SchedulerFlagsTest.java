package com.mesosphere.sdk.scheduler;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

public class SchedulerFlagsTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testUninstallIsEnabled() throws Exception {
        environmentVariables.set("SDK_UNINSTALL", "true");
        SchedulerFlags schedulerFlags = SchedulerFlags.fromEnv();
        assert schedulerFlags.isUninstallEnabled();
        environmentVariables.set("SDK_UNINSTALL", "can be set to anything");
        schedulerFlags = SchedulerFlags.fromEnv();
        assert schedulerFlags.isUninstallEnabled();
    }

    @Test
    public void testUninstallIsDisabled() throws Exception {
        environmentVariables.set("SDK_UNINSTALL", null);
        SchedulerFlags schedulerFlags = SchedulerFlags.fromEnv();
        assert !schedulerFlags.isUninstallEnabled();
    }

}