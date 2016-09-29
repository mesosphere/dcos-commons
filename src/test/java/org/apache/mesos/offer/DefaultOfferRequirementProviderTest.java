package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.InvalidTaskSpecificationException;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

/**
 * This class tests the DefaultOfferRequirementProvider.
 */
public class DefaultOfferRequirementProviderTest {
    private static final int DISK_SIZE_MB = 1000;
    private static final double CPU = 1.0;
    private static final double MEM = 1024.0;
    private DefaultOfferRequirementProvider defaultOfferRequirementProvider;

    @Before
    public void beforeEach() {
        defaultOfferRequirementProvider = new DefaultOfferRequirementProvider();
    }

    @Test
    public void testUnchangedVolumes() throws InvalidTaskSpecificationException, InvalidRequirementException {
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
    public void testChangedVolumes() throws InvalidTaskSpecificationException, InvalidRequirementException {
        Protos.Resource oldVolume = ResourceTestUtils.getExpectedMountVolume(DISK_SIZE_MB);
        Protos.Resource newVolume = ResourceTestUtils.getExpectedMountVolume(DISK_SIZE_MB + 500);
        Protos.TaskInfo oldTaskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(oldVolume));
        Protos.TaskInfo newTaskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(newVolume));
        TaskSpecification newTaskSpecification = DefaultTaskSpecification.create(newTaskInfo);

        defaultOfferRequirementProvider.getExistingOfferRequirement(oldTaskInfo, newTaskSpecification);
    }

    @Test(expected=InvalidRequirementException.class)
    public void testEmptyVolume() throws InvalidTaskSpecificationException, InvalidRequirementException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.Resource oldVolume = ResourceTestUtils.getExpectedMountVolume(DISK_SIZE_MB);
        Protos.TaskInfo oldTaskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(oldVolume));
        Protos.TaskInfo newTaskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));
        TaskSpecification newTaskSpecification = DefaultTaskSpecification.create(newTaskInfo);

        defaultOfferRequirementProvider.getExistingOfferRequirement(oldTaskInfo, newTaskSpecification);
    }

    @Test
    public void testNoVolumes() throws InvalidTaskSpecificationException, InvalidRequirementException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));
        TaskSpecification taskSpecification = DefaultTaskSpecification.create(taskInfo);

        OfferRequirement offerRequirement =
                defaultOfferRequirementProvider.getExistingOfferRequirement(taskInfo, taskSpecification);
        Assert.assertNotNull(offerRequirement);
        Assert.assertFalse(offerRequirement.getPersistenceIds().contains(TestConstants.PERSISTENCE_ID));
        Assert.assertTrue(offerRequirement.getResourceIds().contains(TestConstants.RESOURCE_ID));
    }

    @Test
    public void addNewDesiredResource() throws InvalidTaskSpecificationException, InvalidRequirementException {
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
