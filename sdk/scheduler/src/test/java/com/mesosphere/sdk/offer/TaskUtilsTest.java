package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.TestConstants;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;
import java.util.*;

import javax.validation.ValidationException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class tests the TaskUtils class.
 */
public class TaskUtilsTest {
    private static final String testTaskName = "test-task-name";

    @Test
    public void testValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = Protos.TaskID.newBuilder().setValue(testTaskName + "__id").build();
        Assert.assertEquals(testTaskName, CommonIdUtils.toTaskName(validTaskId));
    }

    @Test(expected = TaskException.class)
    public void testInvalidToTaskName() throws Exception {
        CommonIdUtils.toTaskName(Protos.TaskID.newBuilder().setValue(testTaskName + "_id").build());
    }

    @Test
    public void testAreNotDifferentTaskSpecifications() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec();
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec();
        Assert.assertFalse(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsName() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec();
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                "new" + oldTaskSpecification.getName(),
                oldTaskSpecification.getCommand().get().getValue(),
                oldTaskSpecification.getResourceSet().getId(),
                TestPodFactory.CPU,
                TestPodFactory.MEM,
                TestPodFactory.DISK);

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsCmd() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec();
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                oldTaskSpecification.getName(),
                oldTaskSpecification.getCommand().get().getValue() + " && echo foo",
                oldTaskSpecification.getResourceSet().getId(),
                TestPodFactory.CPU,
                TestPodFactory.MEM,
                TestPodFactory.DISK);

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsResourcesLength() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .cpus(5.)
                        .build());
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .cpus(5.)
                        .memory(6.)
                        .build());

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsNoResourceOverlap() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .cpus(5.)
                        .build());
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .memory(5.)
                        .build());

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreNotDifferentTaskSpecificationsResourcesMatch() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .cpus(5.)
                        .memory(3.)
                        .build());
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID + "b")
                        .cpus(5.)
                        .memory(3.)
                        .build());

        Assert.assertFalse(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsConfigsLength() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec();
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(new DefaultConfigFileSpec(
                        "config", "../relative/path/to/config", "this is a config template")));

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsNoConfigOverlap() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        new DefaultConfigFileSpec("config", "../relative/path/to/config", "this is a config template"),
                        new DefaultConfigFileSpec("config2", "../relative/path/to/config2", "second config")));

        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        new DefaultConfigFileSpec("config", "../diff/path/to/config", "this is a diff config template"),
                        new DefaultConfigFileSpec("config2", "../diff/path/to/config2", "diff second config")));

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreNotDifferentTaskSpecificationsReorderedConfigMatch() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        new DefaultConfigFileSpec("config", "../relative/path/to/config", "a config template"),
                        new DefaultConfigFileSpec("config2", "../relative/path/to/config2", "second config")));

        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        new DefaultConfigFileSpec("config2", "../relative/path/to/config2", "second config"),
                        new DefaultConfigFileSpec("config", "../relative/path/to/config", "a config template")));

        Assert.assertFalse(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsVIP() {
        Protos.Value.Builder portValueBuilder = Protos.Value.newBuilder()
                .setType(Protos.Value.Type.RANGES);
        portValueBuilder.getRangesBuilder().addRangeBuilder()
                .setBegin(80)
                .setEnd(80);

        ResourceSet oldResourceSet = mock(ResourceSet.class);
        ResourceSet newResourceSet = mock(ResourceSet.class);

        ResourceSpec oldVip = new NamedVIPSpec(
                portValueBuilder.build(),
                TestConstants.ROLE,
                TestConstants.PRE_RESERVED_ROLE,
                TestConstants.PRINCIPAL,
                "env-key",
                "port-name",
                "protocol",
                TestConstants.PORT_VISIBILITY,
                TestConstants.VIP_NAME,
                TestConstants.VIP_PORT,
                Arrays.asList("network-name"));

        ResourceSpec newVip = new NamedVIPSpec(
                portValueBuilder.build(),
                TestConstants.ROLE,
                TestConstants.PRE_RESERVED_ROLE,
                TestConstants.PRINCIPAL,
                "env-key",
                "port-name",
                "protocol",
                TestConstants.PORT_VISIBILITY,
                TestConstants.VIP_NAME + "-different", // Different vip name
                TestConstants.VIP_PORT,
                Arrays.asList("network-name"));

        when(oldResourceSet.getId()).thenReturn(TestConstants.RESOURCE_SET_ID);
        when(oldResourceSet.getResources()).thenReturn(Arrays.asList(oldVip)); // Old VIP
        when(oldResourceSet.getVolumes()).thenReturn(Collections.emptyList());

        when(newResourceSet.getId()).thenReturn(TestConstants.RESOURCE_SET_ID);
        when(newResourceSet.getResources()).thenReturn(Arrays.asList(newVip)); // New VIP
        when(newResourceSet.getVolumes()).thenReturn(Collections.emptyList());

        TaskSpec oldTaskSpecification = DefaultTaskSpec.newBuilder(TestPodFactory.getTaskSpec())
                .resourceSet(oldResourceSet)
                .build();

        TaskSpec newTaskSpecification = DefaultTaskSpec.newBuilder(TestPodFactory.getTaskSpec())
                .resourceSet(newResourceSet)
                .build();

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test(expected=ValidationException.class)
    public void testConfigsSamePathFailsValidation() {
        TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        new DefaultConfigFileSpec("config", "../relative/path/to/config", "this is a config template"),
                        new DefaultConfigFileSpec("config2", "../relative/path/to/config", "same path should fail")));
    }

    @Test(expected=ValidationException.class)
    public void testConfigsSameNameFailsValidation() {
        TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        new DefaultConfigFileSpec("config", "../relative/path/to/config", "this is a config template"),
                        new DefaultConfigFileSpec("config", "../relative/path/to/config2", "same name should fail")));
    }

    @Test
    public void testTaskLostNeedsRecovery() {
        Protos.TaskStatus taskStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setState(Protos.TaskState.TASK_LOST)
                .build();
        Assert.assertTrue(TaskUtils.isRecoveryNeeded(taskStatus));
    }
}
