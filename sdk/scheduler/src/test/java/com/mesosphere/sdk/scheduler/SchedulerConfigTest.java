package com.mesosphere.sdk.scheduler;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.mesosphere.sdk.framework.EnvStore;

public class SchedulerConfigTest {

    @Test
    public void testUninstall() throws Exception {
        Map<String, String> confMap = getMinimalMap();

        confMap.put("SDK_UNINSTALL", "true");
        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnvStore(EnvStore.fromMap(confMap));
        Assert.assertTrue(schedulerConfig.isUninstallEnabled());

        confMap.put("SDK_UNINSTALL", "can be set to anything");
        schedulerConfig = SchedulerConfig.fromEnvStore(EnvStore.fromMap(confMap));
        Assert.assertTrue(schedulerConfig.isUninstallEnabled());

        confMap.put("SDK_UNINSTALL", "");
        schedulerConfig = SchedulerConfig.fromEnvStore(EnvStore.fromMap(confMap));
        Assert.assertTrue(schedulerConfig.isUninstallEnabled());

        confMap.remove("SDK_UNINSTALL");
        schedulerConfig = SchedulerConfig.fromEnvStore(EnvStore.fromMap(confMap));
        Assert.assertFalse(schedulerConfig.isUninstallEnabled());
    }

    @Test
    public void testRegionAwareness() {
        Map<String, String> confMap = getMinimalMap();
        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnvStore(EnvStore.fromMap(confMap));
        Assert.assertFalse(schedulerConfig.isRegionAwarenessEnabled());

        confMap.put("ALLOW_REGION_AWARENESS", "false");
        schedulerConfig = SchedulerConfig.fromEnvStore(EnvStore.fromMap(confMap));
        Assert.assertFalse(schedulerConfig.isRegionAwarenessEnabled());

        confMap.put("ALLOW_REGION_AWARENESS", "true");
        schedulerConfig = SchedulerConfig.fromEnvStore(EnvStore.fromMap(confMap));
        Assert.assertTrue(schedulerConfig.isRegionAwarenessEnabled());

        confMap.remove("ALLOW_REGION_AWARENESS");
        schedulerConfig = SchedulerConfig.fromEnvStore(EnvStore.fromMap(confMap));
        Assert.assertFalse(schedulerConfig.isRegionAwarenessEnabled());
    }

    @Test
    public void testHostnameCustomizations() {
        Map<String, String> confMap = getMinimalMap();
        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnvStore(EnvStore.fromMap(confMap));
        Assert.assertEquals("autoip.dcos.thisdcos.directory", schedulerConfig.getAutoipTLD());
        Assert.assertEquals("l4lb.thisdcos.directory", schedulerConfig.getVipTLD());
        Assert.assertEquals("marathon", schedulerConfig.getMarathonName());

        confMap.put("SERVICE_TLD", "test.autoip.tld");
        confMap.put("VIP_TLD", "test.vip.tld");
        confMap.put("MARATHON_NAME", "mom-1");
        schedulerConfig = SchedulerConfig.fromEnvStore(EnvStore.fromMap(confMap));
        Assert.assertEquals("test.autoip.tld", schedulerConfig.getAutoipTLD());
        Assert.assertEquals("test.vip.tld", schedulerConfig.getVipTLD());
        Assert.assertEquals("mom-1", schedulerConfig.getMarathonName());

    }

    private static Map<String, String> getMinimalMap() {
        Map<String, String> map = new HashMap<>();
        map.put("PACKAGE_NAME", "test-package");
        map.put("PACKAGE_VERSION", "1.5");
        map.put("PACKAGE_BUILD_TIME_EPOCH_MS", "1234567890");
        return map;
    }
}