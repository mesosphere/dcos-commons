package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
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
        Resource resource = ResourceTestUtils.getExpectedScalar(
                "cpus",
                1.0,
                testResourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(resource);
        Assert.assertEquals(1, offerRequirement.getResourceIds().size());
        Assert.assertEquals(testResourceId, offerRequirement.getResourceIds().iterator().next());
    }

    @Test
    public void testOnePersistenceId() throws InvalidRequirementException {
        Resource resource = ResourceTestUtils.getExpectedMountVolume(
                1000,
                TestConstants.PERSISTENCE_ID,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                TestConstants.MOUNT_ROOT,
                TestConstants.CONTAINER_PATH,
                TestConstants.PERSISTENCE_ID);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(resource);
        Assert.assertEquals(1, offerRequirement.getResourceIds().size());
        Assert.assertTrue(offerRequirement.getResourceIds().contains(TestConstants.PERSISTENCE_ID));
        Assert.assertEquals(1, offerRequirement.getPersistenceIds().size());
        Assert.assertEquals(TestConstants.PERSISTENCE_ID, offerRequirement.getPersistenceIds().iterator().next());
    }

    @Test
    public void testOneOfEachId() throws InvalidRequirementException {
        String testResourceId = UUID.randomUUID().toString();
        Resource cpu = ResourceTestUtils.getExpectedScalar(
                "cpus",
                1.0,
                testResourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);
        Resource volume = ResourceTestUtils.getExpectedMountVolume(
                1000,
                TestConstants.PERSISTENCE_ID,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                TestConstants.MOUNT_ROOT,
                TestConstants.CONTAINER_PATH,
                TestConstants.PERSISTENCE_ID);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(
                Arrays.asList(cpu, volume), false);
        Assert.assertEquals(2, offerRequirement.getResourceIds().size());
        Assert.assertTrue(testResourceId, offerRequirement.getResourceIds().contains(testResourceId));
        Assert.assertTrue(TestConstants.PERSISTENCE_ID, offerRequirement.getResourceIds().contains(testResourceId));
        Assert.assertEquals(1, offerRequirement.getPersistenceIds().size());
        Assert.assertEquals(TestConstants.PERSISTENCE_ID, offerRequirement.getPersistenceIds().iterator().next());
    }

    @Test
    public void testExecutor() throws InvalidRequirementException {
        Resource cpu = ResourceTestUtils.getExpectedCpu(1.0);
        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(cpu);
        ExecutorInfo execInfo = TaskTestUtils.getExecutorInfo(cpu);
        OfferRequirement offerRequirement = OfferRequirement.create(
                "taskType",
                0,
                Arrays.asList(new TaskRequirement(taskInfo)),
                Optional.of(ExecutorRequirement.create(execInfo)));
        Resource executorResource = offerRequirement
                .getExecutorRequirementOptional().get()
                .getExecutorInfo()
                .getResourcesList()
                .get(0);

        Assert.assertEquals(cpu, executorResource);
    }
}
