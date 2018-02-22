package com.mesosphere.sdk.scheduler;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class SchedulerConfigTest {

    @Test
    public void testUninstallIsEnabled() throws Exception {
        Map<String, String> confMap = getMinimalMap();
        confMap.put("SDK_UNINSTALL", "true");
        SchedulerConfig schedulerConfig = SchedulerConfig.fromMap(confMap);
        Assert.assertTrue(schedulerConfig.isUninstallEnabled());
        confMap.put("SDK_UNINSTALL", "can be set to anything");
        schedulerConfig = SchedulerConfig.fromMap(confMap);
        Assert.assertTrue(schedulerConfig.isUninstallEnabled());
    }

    @Test
    public void testUninstallIsDisabled() throws Exception {
        Map<String, String> confMap = getMinimalMap();
        confMap.remove("SDK_UNINSTALL");
        SchedulerConfig schedulerConfig = SchedulerConfig.fromMap(confMap);
        Assert.assertFalse(schedulerConfig.isUninstallEnabled());
    }

    @Test
    public void regionAwareness() {
        Map<String, String> confMap = getMinimalMap();
        SchedulerConfig schedulerConfig = SchedulerConfig.fromMap(confMap);
        Assert.assertFalse(schedulerConfig.isRegionAwarenessEnabled());

        confMap.put("ALLOW_REGION_AWARENESS", "false");
        Assert.assertFalse(schedulerConfig.isRegionAwarenessEnabled());

        confMap.put("ALLOW_REGION_AWARENESS", "true");
        Assert.assertTrue(schedulerConfig.isRegionAwarenessEnabled());

        confMap.remove("ALLOW_REGION_AWARENESS");
        Assert.assertFalse(schedulerConfig.isRegionAwarenessEnabled());
    }

    private static Map<String, String> getMinimalMap() {
        Map<String, String> map = new HashMap<>();
        map.put("PACKAGE_NAME", "test-package");
        map.put("PACKAGE_VERSION", "1.5");
        map.put("PACKAGE_BUILD_TIME_EPOCH_MS", "1234567890");
        return map;
    }
}