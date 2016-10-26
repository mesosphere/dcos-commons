package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;

import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
    Assert.assertEquals(TestConstants.RESOURCE_ID, ResourceUtils.getResourceId(taskInfo.getExecutor().getResources(0)));

    taskInfo = ResourceUtils.clearResourceIds(taskInfo);
    Assert.assertNull(ResourceUtils.getResourceId(taskInfo.getResources(0)));
    Assert.assertNull(ResourceUtils.getResourceId(taskInfo.getExecutor().getResources(0)));
  }

  private void validateRanges(List<Protos.Value.Range> expectedRanges, List<Protos.Value.Range> actualRanges) {
    Assert.assertEquals(expectedRanges.size(), actualRanges.size());

    for (int i=0; i<expectedRanges.size(); i++) {
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
