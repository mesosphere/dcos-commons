package org.apache.mesos.offer;

import java.util.Arrays;
import java.util.List;

import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

import org.apache.mesos.offer.TaskRequirement.InvalidTaskRequirementException;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskRequirementTest {
  private static final Logger logger = LoggerFactory.getLogger(TaskRequirementTest.class);

  private static final String testTaskName = "test-task-name";
  private static final String testTaskId = "test-task-id";
  private static final String testSlaveId = "test-slave-id";
  private static final String testExecutorId = "test-executor-id";

  @Test
  public void testValidConstructor() throws Exception {
    Resource resource = ResourceBuilder.cpus(1.0);
    getTaskRequirement(resource);
  }

  @Test(expected=InvalidTaskRequirementException.class)
  public void testInvalidConstructor() throws Exception {
    Resource resource = ResourceBuilder.cpus(1.0);
    TaskInfo invalidTaskInfo = getTaskInfo(resource);

    CommandInfo cmdInfo = CommandInfo.newBuilder().build();

    ExecutorInfo execInfo = ExecutorInfo.newBuilder()
      .setExecutorId(ExecutorID.newBuilder()
          .setValue(testExecutorId).build())
      .setCommand(cmdInfo)
      .build();

    invalidTaskInfo = TaskInfo.newBuilder(invalidTaskInfo)
      .setExecutor(execInfo)
      .build();

    new TaskRequirement(invalidTaskInfo);
  }

  private TaskRequirement getTaskRequirement(Resource resource) throws Exception {
    return getTaskRequirement(Arrays.asList(resource));
  }

  private TaskRequirement getTaskRequirement(List<Resource> resources) throws Exception {
    return new TaskRequirement(getTaskInfo(resources));
  }

  private TaskInfo getTaskInfo(Resource resource) {
    return getTaskInfo(Arrays.asList(resource));
  }

  private TaskInfo getTaskInfo(List<Resource> resources) {
    TaskInfoBuilder builder = new TaskInfoBuilder(testTaskId, testTaskName, testSlaveId);

    for (Resource resource : resources) {
      logger.info("Resource: {}", resource);
      builder.addResource(resource);
    }

    return builder.build();
  }
}
