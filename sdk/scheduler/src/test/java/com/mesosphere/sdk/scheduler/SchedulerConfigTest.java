package com.mesosphere.sdk.scheduler;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

public class SchedulerConfigTest {

    @Test
    public void testUninstallIsEnabled() throws Exception {
        SchedulerConfig schedulerConfig = SchedulerConfig.fromMap(Collections.singletonMap("SDK_UNINSTALL", "true"));
        Assert.assertTrue(schedulerConfig.isUninstallEnabled());
        schedulerConfig = SchedulerConfig.fromMap(Collections.singletonMap("SDK_UNINSTALL", "can be set to anything"));
        Assert.assertTrue(schedulerConfig.isUninstallEnabled());
    }

    @Test
    public void testUninstallIsDisabled() throws Exception {
        SchedulerConfig schedulerConfig = SchedulerConfig.fromMap(Collections.emptyMap());
        Assert.assertFalse(schedulerConfig.isUninstallEnabled());
    }

    @Test
    public void regionAwareness() {
        SchedulerConfig schedulerConfig = SchedulerConfig.fromMap(Collections.emptyMap());
        Assert.assertFalse(schedulerConfig.isRegionAwarenessEnabled());

        schedulerConfig = SchedulerConfig.fromMap(Collections.singletonMap("ALLOW_REGION_AWARENESS", null));
        Assert.assertFalse(schedulerConfig.isRegionAwarenessEnabled());

        schedulerConfig = SchedulerConfig.fromMap(Collections.singletonMap("ALLOW_REGION_AWARENESS", "false"));
        Assert.assertFalse(schedulerConfig.isRegionAwarenessEnabled());

        schedulerConfig = SchedulerConfig.fromMap(Collections.singletonMap("ALLOW_REGION_AWARENESS", "true"));
        Assert.assertTrue(schedulerConfig.isRegionAwarenessEnabled());
    }
}