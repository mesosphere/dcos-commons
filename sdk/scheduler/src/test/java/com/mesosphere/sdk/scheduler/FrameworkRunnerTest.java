package com.mesosphere.sdk.scheduler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.testutils.TestConstants;

import static org.mockito.Mockito.*;

public class FrameworkRunnerTest {

    @Mock private Capabilities mockCapabilities;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        Capabilities.overrideCapabilities(mockCapabilities);
    }

    @Test
    public void minimalFrameworkInfoInitial() {
        Map<String, String> env = getMinimalMap();
        SchedulerConfig schedulerConfig = SchedulerConfig.fromMap(env);
        FrameworkConfig frameworkConfig = FrameworkConfig.fromMap(env);
        FrameworkRunner runner = new FrameworkRunner(schedulerConfig, frameworkConfig);
        Protos.FrameworkInfo info = runner.getFrameworkInfo(Optional.empty());
        Assert.assertEquals("/path/to/test-service", info.getName());
        Assert.assertEquals("nobody", info.getUser());
        Assert.assertEquals(1209600, info.getFailoverTimeout(), 0.1);
        Assert.assertTrue(info.getCheckpoint());
        Assert.assertEquals("/path/to/test-service-principal", info.getPrincipal());
        Assert.assertFalse(info.hasId());
        Assert.assertEquals("path__to__test-service-role", info.getRole());
        Assert.assertEquals(0, info.getRolesCount());
        Assert.assertEquals(0, info.getCapabilitiesCount());
        Assert.assertFalse(info.hasWebuiUrl());
    }

    @Test
    public void minimalFrameworkInfoRelaunch() {
        Map<String, String> env = getMinimalMap();
        SchedulerConfig schedulerConfig = SchedulerConfig.fromMap(env);
        FrameworkConfig frameworkConfig = FrameworkConfig.fromMap(env);
        FrameworkRunner runner = new FrameworkRunner(schedulerConfig, frameworkConfig);
        Protos.FrameworkInfo info = runner.getFrameworkInfo(Optional.of(TestConstants.FRAMEWORK_ID));
        Assert.assertEquals("/path/to/test-service", info.getName());
        Assert.assertEquals("nobody", info.getUser());
        Assert.assertEquals(1209600, info.getFailoverTimeout(), 0.1);
        Assert.assertTrue(info.getCheckpoint());
        Assert.assertEquals("/path/to/test-service-principal", info.getPrincipal());
        Assert.assertEquals(TestConstants.FRAMEWORK_ID, info.getId());
        Assert.assertEquals("path__to__test-service-role", info.getRole());
        Assert.assertEquals(0, info.getRolesCount());
        Assert.assertEquals(0, info.getCapabilitiesCount());
        Assert.assertFalse(info.hasWebuiUrl());
    }

    @Test
    public void exhaustiveFrameworkInfo() {
        Map<String, String> env = getMinimalMap();
        env.put("FRAMEWORK_PRINCIPAL", "custom-principal");
        env.put("FRAMEWORK_USER", "custom-user");
        env.put("FRAMEWORK_PRERESERVED_ROLES", "role1,role2,role3");
        env.put("FRAMEWORK_WEB_URL", "custom-url");
        env.put("FRAMEWORK_GPUS", "True");
        SchedulerConfig schedulerConfig = SchedulerConfig.fromMap(env);
        FrameworkConfig frameworkConfig = FrameworkConfig.fromMap(env);

        when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        when(mockCapabilities.supportsPreReservedResources()).thenReturn(true);
        when(mockCapabilities.supportsRegionAwareness(schedulerConfig)).thenReturn(true);
        when(mockCapabilities.supportsGpuResource()).thenReturn(true);

        FrameworkRunner runner = new FrameworkRunner(schedulerConfig, frameworkConfig);
        Protos.FrameworkInfo info = runner.getFrameworkInfo(Optional.of(TestConstants.FRAMEWORK_ID));
        Assert.assertEquals("/path/to/test-service", info.getName());
        Assert.assertEquals("custom-user", info.getUser());
        Assert.assertEquals(1209600, info.getFailoverTimeout(), 0.1);
        Assert.assertTrue(info.getCheckpoint());
        Assert.assertEquals("custom-principal", info.getPrincipal());
        Assert.assertEquals(TestConstants.FRAMEWORK_ID, info.getId());
        Assert.assertFalse(info.hasRole());
        Assert.assertEquals(Arrays.asList("path__to__test-service-role", "role1", "role2", "role3"), info.getRolesList());
        Assert.assertEquals(Arrays.asList(
                getCapability(Protos.FrameworkInfo.Capability.Type.MULTI_ROLE),
                getCapability(Protos.FrameworkInfo.Capability.Type.GPU_RESOURCES),
                getCapability(Protos.FrameworkInfo.Capability.Type.RESERVATION_REFINEMENT),
                getCapability(Protos.FrameworkInfo.Capability.Type.REGION_AWARE)), info.getCapabilitiesList());
        Assert.assertEquals("custom-url", info.getWebuiUrl());
    }

    private static Protos.FrameworkInfo.Capability getCapability(Protos.FrameworkInfo.Capability.Type type) {
        return Protos.FrameworkInfo.Capability.newBuilder().setType(type).build();
    }

    private static Map<String, String> getMinimalMap() {
        Map<String, String> map = new HashMap<>();
        map.put("FRAMEWORK_NAME", "/path/to/test-service");
        map.put("PACKAGE_NAME", "test-package");
        map.put("PACKAGE_VERSION", "1.5");
        map.put("PACKAGE_BUILD_TIME_EPOCH_MS", "1234567890");
        return map;
    }
}
