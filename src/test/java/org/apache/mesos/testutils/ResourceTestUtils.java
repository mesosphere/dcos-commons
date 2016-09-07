package org.apache.mesos.testutils;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.offer.ResourceUtils;

import java.util.Arrays;

public class ResourceTestUtils {
  public static Resource getDesiredRootVolume(double diskSize) {
    return ResourceUtils.getDesiredRootVolume(
            TestConstants.role,
            TestConstants.principal,
            diskSize,
            TestConstants.containerPath);
  }

  public static Resource getDesiredMountVolume(double diskSize) {
    return ResourceUtils.getDesiredMountVolume(
            TestConstants.role,
            TestConstants.principal,
            diskSize,
            TestConstants.containerPath);
  }

  public static Resource getUnreservedMountVolume(double diskSize) {
    return ResourceUtils.getUnreservedMountVolume(diskSize, TestConstants.mountRoot);
  }

  public static Resource getExpectedMountVolume(double diskSize) {
    return getExpectedMountVolume(diskSize, TestConstants.resourceId);
  }

  public static Resource getExpectedMountVolume(double diskSize, String resourceId) {
    return ResourceUtils.getExpectedMountVolume(
            diskSize,
            resourceId,
            TestConstants.role,
            TestConstants.principal,
            TestConstants.mountRoot,
            TestConstants.persistenceId);
  }

  public static Resource getExpectedRootVolume(double diskSize) {
    return getExpectedRootVolume(diskSize, TestConstants.resourceId);
  }

  public static Resource getExpectedRootVolume(double diskSize, String resourceId) {
    return ResourceUtils.getExpectedRootVolume(
            diskSize,
            resourceId,
            TestConstants.role,
            TestConstants.principal,
            TestConstants.persistenceId);
  }

  public static Resource getDesiredScalar(String name, double value) {
    return ResourceUtils.getDesiredScalar(
            TestConstants.role,
            TestConstants.principal,
            name,
            value);
  }

  public static Resource getExpectedScalar(String name, double value, String resourceId) {
    return ResourceUtils.getExpectedScalar(
            name,
            value,
            resourceId,
            TestConstants.role,
            TestConstants.principal);
  }

  public static final Resource getDesiredRanges(String name, long begin, long end) {
    return ResourceUtils.getDesiredRanges(
            TestConstants.role,
            TestConstants.principal,
            name,
            Arrays.asList(Protos.Value.Range.newBuilder().setBegin(begin).setEnd(end).build()));
  }

  public static Resource getUnreservedCpu(double cpus) {
    return ResourceUtils.getUnreservedScalar("cpus", cpus);
  }

  public static Resource getUnreservedMem(double mem) {
    return ResourceUtils.getUnreservedScalar("mem", mem);
  }

  public static Resource getUnreservedDisk(double disk) {
    return ResourceUtils.getUnreservedScalar("disk", disk);
  }

  public static Resource getExpectedCpu(double cpus) {
    return ResourceTestUtils.getExpectedScalar("cpus", cpus, TestConstants.resourceId);
  }

  public static Resource getDesiredCpu(double cpus) {
    return ResourceTestUtils.getDesiredScalar("cpus", cpus);
  }

  public static Resource getDesiredMem(double mem) {
    return ResourceTestUtils.getDesiredScalar("mem", mem);
  }

}
