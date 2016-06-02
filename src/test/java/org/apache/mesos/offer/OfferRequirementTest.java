package org.apache.mesos.offer;

import java.util.Arrays;
import java.util.List;

import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

import org.apache.mesos.offer.TaskRequirement.InvalidTaskRequirementException;
import org.apache.mesos.protobuf.ExecutorInfoBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import org.junit.Assert;
import org.junit.Test;

public class OfferRequirementTest {

  @Test
  public void testConstructor() throws InvalidTaskRequirementException {
    Resource resource = ResourceBuilder.cpus(1.0);
    OfferRequirement offerRequirement = getOfferRequirement(resource);
    Assert.assertNotNull(offerRequirement);
  }

  @Test
  public void testNoIds() throws InvalidTaskRequirementException {
    Resource resource = ResourceBuilder.cpus(1.0);
    OfferRequirement offerRequirement = getOfferRequirement(resource);
    Assert.assertEquals(0, offerRequirement.getResourceIds().size());
  }

  @Test
  public void testOneResourceId() throws InvalidTaskRequirementException {
    Resource resource = ResourceBuilder.reservedCpus(1.0, ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, ResourceTestUtils.testResourceId);
    OfferRequirement offerRequirement = getOfferRequirement(resource);
    Assert.assertEquals(1, offerRequirement.getResourceIds().size());
    Assert.assertEquals(ResourceTestUtils.testResourceId, offerRequirement.getResourceIds().iterator().next());
  }

  @Test
  public void testOnePersistenceId() throws InvalidTaskRequirementException {
    Resource resource = ResourceBuilder.volume(1000.0, ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, ResourceTestUtils.testContainerPath, ResourceTestUtils.testPersistenceId);
    OfferRequirement offerRequirement = getOfferRequirement(resource);
    Assert.assertEquals(1, offerRequirement.getResourceIds().size());
    Assert.assertTrue(ResourceTestUtils.testResourceId, offerRequirement.getResourceIds().contains(ResourceTestUtils.testPersistenceId));
    Assert.assertEquals(1, offerRequirement.getPersistenceIds().size());
    Assert.assertEquals(ResourceTestUtils.testPersistenceId, offerRequirement.getPersistenceIds().iterator().next());
  }

  @Test
  public void testOneOfEachId() throws InvalidTaskRequirementException {
    Resource cpu = ResourceBuilder.reservedCpus(1.0, ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, ResourceTestUtils.testResourceId);
    Resource volume = ResourceBuilder.volume(1000.0, ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, ResourceTestUtils.testContainerPath, ResourceTestUtils.testPersistenceId);
    OfferRequirement offerRequirement = getOfferRequirement(Arrays.asList(cpu, volume));
    Assert.assertEquals(2, offerRequirement.getResourceIds().size());
    Assert.assertTrue(ResourceTestUtils.testResourceId, offerRequirement.getResourceIds().contains(ResourceTestUtils.testResourceId));
    Assert.assertTrue(ResourceTestUtils.testPersistenceId, offerRequirement.getResourceIds().contains(ResourceTestUtils.testResourceId));
    Assert.assertEquals(1, offerRequirement.getPersistenceIds().size());
    Assert.assertEquals(ResourceTestUtils.testPersistenceId, offerRequirement.getPersistenceIds().iterator().next());
  }

  @Test
  public void testExecutor() throws InvalidTaskRequirementException {
    Resource cpu = ResourceBuilder.reservedCpus(1.0, ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, ResourceTestUtils.testResourceId);
    TaskInfo taskInfo = getTaskInfo(cpu);
    ExecutorInfo execInfo = getExecutorInfo(cpu);
    OfferRequirement offerRequirement = new OfferRequirement(Arrays.asList(taskInfo), execInfo);
    Resource executorResource = offerRequirement
        .getExecutorRequirement()
        .getExecutorInfo()
        .getResourcesList()
        .get(0);

    Assert.assertEquals(cpu, executorResource);
  }

  private OfferRequirement getOfferRequirement(Resource resource) throws InvalidTaskRequirementException {
    return getOfferRequirement(Arrays.asList(resource));
  }

  private OfferRequirement getOfferRequirement(List<Resource> resources) throws InvalidTaskRequirementException {
    return new OfferRequirement(Arrays.asList(getTaskInfo(resources)));
  }

  private TaskInfo getTaskInfo(Resource resource) {
    return getTaskInfo(Arrays.asList(resource));
  }

  private TaskInfo getTaskInfo(List<Resource> resources) {
    TaskInfoBuilder builder = new TaskInfoBuilder(
        ResourceTestUtils.testTaskId,
        ResourceTestUtils.testTaskName,
        ResourceTestUtils.testSlaveId);
    return builder.addAllResources(resources).build();
  }

  private ExecutorInfo getExecutorInfo(Resource resource) {
    return getExecutorInfo(Arrays.asList(resource));
  }

  private ExecutorInfo getExecutorInfo(List<Resource> resources) {
    CommandInfo cmd = CommandInfo.newBuilder().build();
    ExecutorInfoBuilder builder = new ExecutorInfoBuilder(ResourceTestUtils.testExecutorId, cmd);
    return builder.addAllResources(resources).build();
  }
}
