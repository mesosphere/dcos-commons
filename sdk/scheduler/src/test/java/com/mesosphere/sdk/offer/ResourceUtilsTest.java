package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ResourceUtilsTest {

    @Test
    public void testCreateDesiredMountVolume() {
        Resource desiredMountVolume = ResourceUtils.getDesiredMountVolume(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                1000,
                TestConstants.CONTAINER_PATH);
        Assert.assertNotNull(desiredMountVolume);
        Assert.assertTrue(desiredMountVolume.getDisk().hasPersistence());
        Assert.assertEquals("", desiredMountVolume.getDisk().getPersistence().getId());
        Assert.assertEquals("", new ResourceRequirement(desiredMountVolume).getResourceId());
        Assert.assertEquals(Source.Type.MOUNT, desiredMountVolume.getDisk().getSource().getType());
    }

    @Test
    public void testCreateDesiredRootVolume() {
        Resource desiredRootVolume = ResourceUtils.getDesiredRootVolume(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                1000,
                TestConstants.CONTAINER_PATH);
        Assert.assertNotNull(desiredRootVolume);
        Assert.assertTrue(desiredRootVolume.getDisk().hasPersistence());
        Assert.assertEquals("", desiredRootVolume.getDisk().getPersistence().getId());
        Assert.assertEquals("", new ResourceRequirement(desiredRootVolume).getResourceId());
        Assert.assertFalse(desiredRootVolume.getDisk().hasSource());
    }

    @Test
    public void testGetUnreservedRanges() {
        List<Protos.Value.Range> testRanges = getTestRanges();
        Resource resource = ResourceUtils.getUnreservedRanges("ports", testRanges);
        validateRanges(testRanges, resource.getRanges().getRangeList());
    }

    @Test
    public void testGetDesiredRanges() {
        List<Protos.Value.Range> testRanges = getTestRanges();
        Resource resource = ResourceUtils.getDesiredRanges(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                "ports",
                testRanges);

        validateRanges(testRanges, resource.getRanges().getRangeList());
        validateRolePrincipal(resource);
    }

    @Test
    public void testGetExpectedRanges() {
        String expectedResourceId = UUID.randomUUID().toString();
        List<Protos.Value.Range> testRanges = getTestRanges();
        Resource resource = ResourceUtils.getExpectedRanges(
                "ports",
                testRanges,
                expectedResourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);

        validateRanges(testRanges, resource.getRanges().getRangeList());
        validateRolePrincipal(resource);
        Assert.assertEquals(expectedResourceId, ResourceUtils.getResourceId(resource));
    }

    @Test
    public void testClearTaskInfoResourceIds() {
        Resource resource = ResourceUtils.getDesiredScalar(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                "cpus",
                1.0);
        resource = ResourceUtils.setResourceId(resource, TestConstants.RESOURCE_ID);

        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder()
                .setName(TestConstants.TASK_NAME)
                .setTaskId(TestConstants.TASK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .addResources(resource)
                .build();
        Assert.assertEquals(TestConstants.RESOURCE_ID, ResourceUtils.getResourceId(taskInfo.getResources(0)));

        taskInfo = ResourceUtils.clearResourceIds(taskInfo);
        Assert.assertNull(ResourceUtils.getResourceId(taskInfo.getResources(0)));
    }

    @Test
    public void testClearExecutorInfoResourceIds() {
        Resource resource = ResourceUtils.getDesiredScalar(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                "cpus",
                1.0);
        resource = ResourceUtils.setResourceId(resource, TestConstants.RESOURCE_ID);

        Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo.newBuilder()
                .setExecutorId(TestConstants.EXECUTOR_ID)
                .setCommand(Protos.CommandInfo.newBuilder().build())
                .addResources(resource)
                .build();
        Assert.assertEquals(TestConstants.RESOURCE_ID, ResourceUtils.getResourceId(executorInfo.getResources(0)));

        executorInfo = ResourceUtils.clearResourceIds(executorInfo);
        Assert.assertNull(ResourceUtils.getResourceId(executorInfo.getResources(0)));
    }

    @Test
    public void testClearTaskInfoAndExecutorInfoResourceIds() {
        Resource resource = ResourceUtils.getDesiredScalar(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                "cpus",
                1.0);
        resource = ResourceUtils.setResourceId(resource, TestConstants.RESOURCE_ID);

        Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo.newBuilder()
                .setExecutorId(TestConstants.EXECUTOR_ID)
                .setCommand(Protos.CommandInfo.newBuilder().build())
                .addResources(resource)
                .build();
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder()
                .setName(TestConstants.TASK_NAME)
                .setTaskId(TestConstants.TASK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .addResources(resource)
                .setExecutor(executorInfo)
                .build();

        Assert.assertEquals(TestConstants.RESOURCE_ID, ResourceUtils.getResourceId(taskInfo.getResources(0)));
        Assert.assertEquals(
                TestConstants.RESOURCE_ID, ResourceUtils.getResourceId(taskInfo.getExecutor().getResources(0)));

        taskInfo = ResourceUtils.clearResourceIds(taskInfo);
        Assert.assertNull(ResourceUtils.getResourceId(taskInfo.getResources(0)));
        Assert.assertNull(ResourceUtils.getResourceId(taskInfo.getExecutor().getResources(0)));
    }

    @Test
    public void testGetAllResources() {
        final String RESERVED_RESOURCE_1_ID = "reserved-resource-id";
        final String RESERVED_RESOURCE_2_ID = "reserved-volume-id";
        final String RESERVED_RESOURCE_3_ID = "reserved-cpu-id";
        final String RESERVED_RESOURCE_4_ID = "reserved-volume-id2";
        final Protos.Resource RESERVED_RESOURCE_1 = ResourceUtils.getExpectedRanges(
                "ports",
                Collections.singletonList(Protos.Value.Range.newBuilder().setBegin(123).setEnd(234).build()),
                RESERVED_RESOURCE_1_ID,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);
        final Protos.Resource RESERVED_RESOURCE_2 = ResourceUtils.getExpectedRootVolume(
                999.0,
                RESERVED_RESOURCE_2_ID,
                TestConstants.CONTAINER_PATH,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                RESERVED_RESOURCE_2_ID);
        final Protos.Resource RESERVED_RESOURCE_3 = ResourceUtils.getExpectedScalar(
                "cpus",
                1.0,
                RESERVED_RESOURCE_3_ID,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);
        final Protos.Resource RESERVED_RESOURCE_4 = ResourceUtils.getExpectedRootVolume(
                999.0,
                RESERVED_RESOURCE_4_ID,
                TestConstants.CONTAINER_PATH,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                RESERVED_RESOURCE_4_ID);
        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(RESERVED_RESOURCE_1,
                RESERVED_RESOURCE_2, RESERVED_RESOURCE_3));
        Protos.ExecutorInfo executorInfo = TaskTestUtils.getExecutorInfo(RESERVED_RESOURCE_4);
        final Protos.TaskInfo task = Protos.TaskInfo.newBuilder(taskInfo).setExecutor(executorInfo).build();
        List<String> expectedIds = Arrays.asList(RESERVED_RESOURCE_1_ID, RESERVED_RESOURCE_2_ID, RESERVED_RESOURCE_3_ID, RESERVED_RESOURCE_4_ID);
        List<String> actualIds = ResourceUtils.getAllResources(task).stream().map(ResourceUtils::getResourceId).collect(Collectors.toList());
        Assert.assertArrayEquals(expectedIds.toArray(), actualIds.toArray());
    }

    private void validateRanges(List<Protos.Value.Range> expectedRanges, List<Protos.Value.Range> actualRanges) {
        Assert.assertEquals(expectedRanges.size(), actualRanges.size());

        for (int i = 0; i < expectedRanges.size(); i++) {
            Assert.assertEquals(expectedRanges.get(i), actualRanges.get(i));
        }
    }

    private void validateRolePrincipal(Resource resource) {
        Assert.assertEquals(TestConstants.ROLE, resource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, resource.getReservation().getPrincipal());
    }

    public List<Protos.Value.Range> getTestRanges() {
        long begin0 = 1;
        long end0 = 10;
        Protos.Value.Range range0 = Protos.Value.Range.newBuilder()
                .setBegin(begin0)
                .setEnd(end0)
                .build();

        long begin1 = 20;
        long end1 = 30;
        Protos.Value.Range range1 = Protos.Value.Range.newBuilder()
                .setBegin(begin1)
                .setEnd(end1)
                .build();

        return Arrays.asList(range0, range1);
    }
}
