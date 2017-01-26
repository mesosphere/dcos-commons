package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.TestPodFactory;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.specification.DefaultConfigFileSpec;
import com.mesosphere.sdk.specification.DefaultResourceSet;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.util.*;

import javax.validation.ValidationException;

/**
 * This class tests the TaskUtils class.
 */
public class TaskUtilsTest {
    @Rule
    public final EnvironmentVariables environmentVariables = OfferRequirementTestUtils.getApiPortEnvironment();

    private static final String testTaskName = "test-task-name";
    private static final String testTaskId = "test-task-id";
    private static final String testAgentId = "test-agent-id";
    private static final UUID testTargetConfigurationId = UUID.randomUUID();

    @Test
    public void testValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(testTaskName + "__id");
        Assert.assertEquals(testTaskName, CommonTaskUtils.toTaskName(validTaskId));
    }

    @Test(expected = TaskException.class)
    public void testInvalidToTaskName() throws Exception {
        CommonTaskUtils.toTaskName(getTaskId(testTaskName + "_id"));
    }

    @Test(expected = TaskException.class)
    public void testGetTargetConfigurationFailure() throws Exception {
        CommonTaskUtils.getTargetConfiguration(getTestTaskInfo());
    }

    @Test
    public void testSetTargetConfiguration() throws Exception {
        Protos.TaskInfo taskInfo = CommonTaskUtils.setTargetConfiguration(
                getTestTaskInfo().toBuilder(), testTargetConfigurationId).build();
        Assert.assertEquals(testTargetConfigurationId, CommonTaskUtils.getTargetConfiguration(taskInfo));
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
                DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .cpus(5.)
                        .build());
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
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
                DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .cpus(5.)
                        .build());
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
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
                DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .cpus(5.)
                        .memory(3.)
                        .build());
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
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

    private static Protos.TaskID getTaskId(String value) {
        return Protos.TaskID.newBuilder().setValue(value).build();
    }

    private static Protos.TaskInfo getTestTaskInfo() {
        return Protos.TaskInfo.newBuilder()
                .setName(testTaskName)
                .setTaskId(Protos.TaskID.newBuilder().setValue(testTaskId))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(testAgentId))
                .build();
    }
}
