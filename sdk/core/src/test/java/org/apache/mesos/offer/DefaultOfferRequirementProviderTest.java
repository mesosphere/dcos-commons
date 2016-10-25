package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PassthroughGenerator;
import org.apache.mesos.offer.constrain.PassthroughRule;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.InvalidTaskSpecificationException;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.util.Arrays;
import java.util.Optional;

/**
 * This class tests the DefaultOfferRequirementProvider.
 */
public class DefaultOfferRequirementProviderTest {
    private static final int DISK_SIZE_MB = 1000;
    private static final double CPU = 1.0;
    private static final double MEM = 1024.0;
    private static final PlacementRuleGenerator ALLOW_ALL = new PassthroughGenerator(new PassthroughRule("test"));
    private DefaultOfferRequirementProvider defaultOfferRequirementProvider;
    private EnvironmentVariables environmentVariables;

    @Before
    public void beforeEach() {
        defaultOfferRequirementProvider = new DefaultOfferRequirementProvider();
        environmentVariables = new EnvironmentVariables();
        environmentVariables.set("EXECUTOR_URI", "");
    }

    @Test(expected=InvalidTaskSpecificationException.class)
    public void testEmptyTaskInfo() throws InvalidTaskSpecificationException, TaskException {
        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder()
                .setTaskId(TestConstants.TASK_ID)
                .setName(TestConstants.TASK_NAME)
                .setSlaveId(TestConstants.AGENT_ID);
        DefaultTaskSpecification.create(taskInfoBuilder.build());
    }

    @Test(expected=TaskException.class)
    public void testEmptyLabel() throws InvalidTaskSpecificationException, TaskException {
        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder()
                .setTaskId(TestConstants.TASK_ID)
                .setCommand(TestConstants.COMMAND_INFO)
                .setName(TestConstants.TASK_NAME)
                .setSlaveId(TestConstants.AGENT_ID);
        DefaultTaskSpecification.create(taskInfoBuilder.build());
    }

    @Test
    public void testCommandTaskInfo()
            throws InvalidTaskSpecificationException, TaskException, InvalidRequirementException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.Resource mem = ResourceTestUtils.getDesiredMem(MEM);
        Protos.TaskInfo tempTaskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder(tempTaskInfo)
                .clearContainer()
                .addResources(mem)
                .build();

        TaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo);
        OfferRequirement offerRequirement = defaultOfferRequirementProvider.getNewOfferRequirement(taskSpecification);

        Assert.assertEquals(TestConstants.TASK_TYPE, taskSpecification.getType());
        Assert.assertEquals(false, taskSpecification.getContainer().isPresent());
        Assert.assertEquals(true, taskSpecification.getCommand().isPresent());

        Assert.assertEquals(TestConstants.TASK_TYPE, offerRequirement.getTaskType());

        /*
        for (TaskRequirement taskRequirement : offerRequirement.getTaskRequirements()) {
            boolean validCpu = false;
            boolean validMem = false;
            for (ResourceRequirement resourceRequirement : taskRequirement.getResourceRequirements()) {
                validCpu |= resourceRequirement.getResource().equals(cpu);
                validMem |= resourceRequirement.getResource().equals(mem);
            }
            Assert.assertTrue(validCpu);
            Assert.assertTrue(validMem);
        }
        */
    }

    @Test
    public void testContainerTaskInfo()
            throws InvalidTaskSpecificationException, TaskException, InvalidRequirementException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.Resource mem = ResourceTestUtils.getDesiredMem(MEM);
        Protos.TaskInfo tempTaskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder(tempTaskInfo)
                .clearCommand()
                .addResources(mem)
                .build();

        TaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo);
        OfferRequirement offerRequirement = defaultOfferRequirementProvider.getNewOfferRequirement(taskSpecification);

        Assert.assertEquals(TestConstants.TASK_TYPE, taskSpecification.getType());
        Assert.assertEquals(false, taskSpecification.getCommand().isPresent());
        Assert.assertEquals(true, taskSpecification.getContainer().isPresent());

        Assert.assertEquals(TestConstants.TASK_TYPE, offerRequirement.getTaskType());
    }

    @Test
    public void testCommandContainerTaskInfo()
            throws InvalidTaskSpecificationException, TaskException, InvalidRequirementException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.Resource mem = ResourceTestUtils.getDesiredMem(MEM);
        Protos.TaskInfo tempTaskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder(tempTaskInfo)
                .addResources(mem)
                .build();

        TaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo);
        OfferRequirement offerRequirement = defaultOfferRequirementProvider.getNewOfferRequirement(taskSpecification);

        Assert.assertEquals(TestConstants.TASK_TYPE, taskSpecification.getType());
        Assert.assertEquals(true, taskSpecification.getCommand().isPresent());
        Assert.assertEquals(true, taskSpecification.getContainer().isPresent());

        Assert.assertEquals(TestConstants.TASK_TYPE, offerRequirement.getTaskType());
    }

    @Test
    public void testUnchangedVolumes()
            throws InvalidTaskSpecificationException, InvalidRequirementException, TaskException {
        Protos.Resource volume = ResourceTestUtils.getExpectedMountVolume(DISK_SIZE_MB);
        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(volume));
        TaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo);

        OfferRequirement offerRequirement =
                defaultOfferRequirementProvider.getExistingOfferRequirement(taskInfo, taskSpecification);
        Assert.assertNotNull(offerRequirement);
        Assert.assertTrue(offerRequirement.getPersistenceIds().contains(TestConstants.PERSISTENCE_ID));
        Assert.assertTrue(offerRequirement.getResourceIds().contains(TestConstants.RESOURCE_ID));
    }

    @Test(expected=InvalidRequirementException.class)
    public void testChangedVolumes()
            throws InvalidTaskSpecificationException, InvalidRequirementException, TaskException {
        Protos.Resource oldVolume = ResourceTestUtils.getExpectedMountVolume(DISK_SIZE_MB);
        Protos.Resource newVolume = ResourceTestUtils.getExpectedMountVolume(DISK_SIZE_MB + 500);
        Protos.TaskInfo oldTaskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(oldVolume));
        Protos.TaskInfo newTaskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(newVolume));
        TaskSpecification newTaskSpecification = DefaultTaskSpecification.create(newTaskInfo);

        defaultOfferRequirementProvider.getExistingOfferRequirement(oldTaskInfo, newTaskSpecification);
    }

    @Test(expected=InvalidRequirementException.class)
    public void testEmptyVolume() throws InvalidTaskSpecificationException, InvalidRequirementException, TaskException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.Resource oldVolume = ResourceTestUtils.getExpectedMountVolume(DISK_SIZE_MB);
        Protos.TaskInfo oldTaskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(oldVolume));
        Protos.TaskInfo newTaskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));
        TaskSpecification newTaskSpecification = DefaultTaskSpecification.create(newTaskInfo);

        defaultOfferRequirementProvider.getExistingOfferRequirement(oldTaskInfo, newTaskSpecification);
    }

    @Test
    public void testNoVolumes() throws InvalidTaskSpecificationException, InvalidRequirementException, TaskException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));
        TaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo);

        OfferRequirement offerRequirement =
                defaultOfferRequirementProvider.getExistingOfferRequirement(taskInfo, taskSpecification);
        Assert.assertNotNull(offerRequirement);
        Assert.assertFalse(offerRequirement.getPersistenceIds().contains(TestConstants.PERSISTENCE_ID));
        Assert.assertTrue(offerRequirement.getResourceIds().contains(TestConstants.RESOURCE_ID));
        Assert.assertFalse(offerRequirement.getPlacementRuleGeneratorOptional().isPresent());
    }

    @Test
    public void testPlacementPassthru()
            throws InvalidTaskSpecificationException, InvalidRequirementException, TaskException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));

        TaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo, Optional.of(ALLOW_ALL));

        OfferRequirement offerRequirement =
                defaultOfferRequirementProvider.getExistingOfferRequirement(taskInfo, taskSpecification);
        Assert.assertNotNull(offerRequirement);
        Assert.assertFalse(offerRequirement.getPersistenceIds().contains(TestConstants.PERSISTENCE_ID));
        Assert.assertTrue(offerRequirement.getResourceIds().contains(TestConstants.RESOURCE_ID));
        Assert.assertTrue(offerRequirement.getPlacementRuleGeneratorOptional().isPresent());
    }

    @Test
    public void testAddNewDesiredResource()
            throws InvalidTaskSpecificationException, InvalidRequirementException, TaskException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.Resource mem = ResourceTestUtils.getDesiredMem(MEM);
        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));

        // Add memory requirement to the new TaskSpecification
        TaskSpecification taskSpecification = DefaultTaskSpecification.create(
                Protos.TaskInfo.newBuilder(taskInfo)
                .addResources(mem)
                .build());

        OfferRequirement offerRequirement =
                defaultOfferRequirementProvider.getExistingOfferRequirement(taskInfo, taskSpecification);
        Assert.assertNotNull(offerRequirement);
        Assert.assertFalse(offerRequirement.getPersistenceIds().contains(TestConstants.PERSISTENCE_ID));
        Assert.assertTrue(offerRequirement.getResourceIds().contains(TestConstants.RESOURCE_ID));
    }
}
