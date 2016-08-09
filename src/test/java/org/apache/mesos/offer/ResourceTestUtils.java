package org.apache.mesos.offer;

import org.apache.mesos.Protos.Resource;
import org.apache.mesos.executor.ExecutorUtils;

public class ResourceTestUtils {
  public static final String testContainerPath = "test-container-path";
  public static final String testExecutorName = "test-executor-name";
  public static final String testFrameworkId = "test-framework-id";
  public static final String testHostname = "test-hostname";
  public static final String testMountRoot = "test-mount-root";
  public static final String testOfferId = "test-offer-id";
  public static final String testPersistenceId = "test-persistence-id";
  public static final String testPrincipal = "test-principal";
  public static final String testRole = "test-role";
  public static final String testSlaveId = "test-slave-id";
  public static final String testTaskName = "test-task-name";
  public static final String testResourceId = "test-resource-id";

  public static final String testExecutorId =
          ExecutorUtils.toExecutorId(testExecutorName).getValue();
  public static final String testTaskId = TaskUtils.toTaskId(testTaskName).getValue();

  public static Resource getOfferedUnreservedMountVolume(double diskSize) {
    return ResourceUtils.getUnreservedMountVolume(diskSize, testMountRoot);
  }

  public static Resource getExpectedMountVolume(double diskSize, String resourceId) {
    return ResourceUtils.getExpectedMountVolume(
            diskSize,
            resourceId,
            testRole,
            testPrincipal,
            testMountRoot,
            testPersistenceId);
  }

  public static Resource getExpectedRootVolume(double diskSize, String resourceId) {
    return ResourceUtils.getExpectedRootVolume(diskSize, resourceId, testRole, testPrincipal,testPersistenceId);
  }

  public static Resource getExpectedScalar(String name, double value, String resourceId) {
    return ResourceUtils.getExpectedScalar(name, value, resourceId, testRole, testPrincipal);
  }
}
