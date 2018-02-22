package com.mesosphere.sdk.scheduler;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class SchedulerConfigTest {

    @Test
    public void testUninstallIsEnabled() throws Exception {
        Map<String, String> confMap = new HashMap<>();
        confMap.put("SDK_UNINSTALL", "true");
        SchedulerConfig schedulerConfig = SchedulerConfig.fromMap(confMap);
        Assert.assertTrue(schedulerConfig.isUninstallEnabled());
        confMap.put("SDK_UNINSTALL", "can be set to anything");
        schedulerConfig = SchedulerConfig.fromMap(confMap);
        Assert.assertTrue(schedulerConfig.isUninstallEnabled());
    }

    @Test
    public void testUninstallIsDisabled() throws Exception {
        Map<String, String> confMap = new HashMap<>();
        confMap.remove("SDK_UNINSTALL");
        SchedulerConfig schedulerConfig = SchedulerConfig.fromMap(confMap);
        Assert.assertFalse(schedulerConfig.isUninstallEnabled());
    }

    @Test
    public void regionAwareness() {
        Map<String, String> confMap = new HashMap<>();
        SchedulerConfig schedulerConfig = SchedulerConfig.fromMap(confMap);
        Assert.assertFalse(schedulerConfig.isRegionAwarenessEnabled());

        confMap.put("ALLOW_REGION_AWARENESS", "false");
        Assert.assertFalse(schedulerConfig.isRegionAwarenessEnabled());

        confMap.put("ALLOW_REGION_AWARENESS", "true");
        Assert.assertTrue(schedulerConfig.isRegionAwarenessEnabled());

        confMap.remove("ALLOW_REGION_AWARENESS");
        Assert.assertFalse(schedulerConfig.isRegionAwarenessEnabled());
    }
}