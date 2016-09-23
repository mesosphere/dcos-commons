package org.apache.mesos.offer;

import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.testutils.OfferRequirementTestUtils;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public class OfferRequirementTest {

    @Test
    public void testConstructor() throws InvalidRequirementException {
        Resource resource = ResourceTestUtils.getUnreservedCpu(1.0);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(resource);
        Assert.assertNotNull(offerRequirement);
    }

    @Test
    public void testNoIds() throws InvalidRequirementException {
        Resource resource = ResourceTestUtils.getUnreservedCpu(1.0);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(resource);
        Assert.assertEquals(0, offerRequirement.getResourceIds().size());
    }

    @Test
    public void testOneResourceId() throws InvalidRequirementException {
        String testResourceId = UUID.randomUUID().toString();
        Resource resource = ResourceUtils.getExpectedScalar(
                "cpus",
                1.0,
                testResourceId,
                TestConstants.role,
                TestConstants.principal);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(resource);
        Assert.assertEquals(1, offerRequirement.getResourceIds().size());
        Assert.assertEquals(testResourceId, offerRequirement.getResourceIds().iterator().next());
    }

    @Test
    public void testOnePersistenceId() throws InvalidRequirementException {
        Resource resource = ResourceUtils.getExpectedMountVolume(
                1000,
                TestConstants.persistenceId,
                TestConstants.role,
                TestConstants.principal,
                TestConstants.mountRoot,
                TestConstants.containerPath,
                TestConstants.persistenceId);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(resource);
        Assert.assertEquals(1, offerRequirement.getResourceIds().size());
        Assert.assertTrue(offerRequirement.getResourceIds().contains(TestConstants.persistenceId));
        Assert.assertEquals(1, offerRequirement.getPersistenceIds().size());
        Assert.assertEquals(TestConstants.persistenceId, offerRequirement.getPersistenceIds().iterator().next());
    }

    @Test
    public void testOneOfEachId() throws InvalidRequirementException {
        String testResourceId = UUID.randomUUID().toString();
        Resource cpu = ResourceUtils.getExpectedScalar(
                "cpus",
                1.0,
                testResourceId,
                TestConstants.role,
                TestConstants.principal);
        Resource volume = ResourceUtils.getExpectedMountVolume(
                1000,
                TestConstants.persistenceId,
                TestConstants.role,
                TestConstants.principal,
                TestConstants.mountRoot,
                TestConstants.containerPath,
                TestConstants.persistenceId);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(Arrays.asList(cpu, volume));
        Assert.assertEquals(2, offerRequirement.getResourceIds().size());
        Assert.assertTrue(testResourceId, offerRequirement.getResourceIds().contains(testResourceId));
        Assert.assertTrue(TestConstants.persistenceId, offerRequirement.getResourceIds().contains(testResourceId));
        Assert.assertEquals(1, offerRequirement.getPersistenceIds().size());
        Assert.assertEquals(TestConstants.persistenceId, offerRequirement.getPersistenceIds().iterator().next());
    }

    @Test
    public void testExecutor() throws InvalidRequirementException {
        Resource cpu = ResourceTestUtils.getExpectedCpu(1.0);
        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(cpu);
        ExecutorInfo execInfo = TaskTestUtils.getExecutorInfo(cpu);
        OfferRequirement offerRequirement = new OfferRequirement(Arrays.asList(taskInfo), Optional.of(execInfo));
        Resource executorResource = offerRequirement
                .getExecutorRequirement()
                .getExecutorInfo()
                .getResourcesList()
                .get(0);

        Assert.assertEquals(cpu, executorResource);
    }
}
