package org.apache.mesos.offer;

import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;
import org.apache.mesos.Protos.Resource.DiskInfo.Source.Mount;
import org.apache.mesos.Protos.Resource.ReservationInfo;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.executor.ExecutorUtils;
import org.apache.mesos.protobuf.ValueBuilder;

public class ResourceTestUtils {
  public static final String testMountRoot = "test-mount-root";
  public static final String testRole = "test-role";
  public static final String testPrincipal = "test-principal";
  public static final String testResourceId = "test-resource-id";
  public static final String testPersistenceId = "test-persistence-id";
  public static final String testOfferId = "test-offer-id";
  public static final String testFrameworkId = "test-framework-id";
  public static final String testTaskName = "test-task-name";
  public static final String testTaskId = TaskUtils.toTaskId(testTaskName).getValue();
  public static final String testExecutorName = "test-executor-name";
  public static final String testExecutorId =
      ExecutorUtils.toExecutorId(testExecutorName).getValue();
  public static final String testSlaveId = "test-slave-id";
  public static final String testHostname = "test-hostname";
  public static final String testContainerPath = "test-container-path";

  public static Resource getOfferedUnreservedMountVolume(double diskSize) {
    Value diskValue = new ValueBuilder(Value.Type.SCALAR).setScalar(diskSize).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource("disk", diskValue));
    resBuilder.setRole("*");
    resBuilder.setDisk(getOfferedMountVolumeDiskInfo());

    return resBuilder.build();
  }

  public static Resource getExpectedMountVolume(double diskSize) {
    Value diskValue = new ValueBuilder(Value.Type.SCALAR).setScalar(diskSize).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource("disk", diskValue));
    resBuilder.setRole(testRole);
    resBuilder.setDisk(getExpectedMountVolumeDiskInfo());
    resBuilder.setReservation(getExpectedReservationInfo());

    return resBuilder.build();
  }

  public static Resource getOfferedUnreservedRootVolume(double diskSize) {
    Value diskValue = new ValueBuilder(Value.Type.SCALAR).setScalar(diskSize).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource("disk", diskValue));
    resBuilder.setRole("*");
    return resBuilder.build();
  }

  public static Resource getExpectedRootVolume(double diskSize) {
    Value diskValue = new ValueBuilder(Value.Type.SCALAR).setScalar(diskSize).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource("disk", diskValue));
    resBuilder.setRole(testRole);
    resBuilder.setDisk(getExpectedRootVolumeDiskInfo());
    resBuilder.setReservation(getExpectedReservationInfo());

    return resBuilder.build();
  }

  public static Resource getOfferedUnreservedScalar(String name, double value) {
    Value val = new ValueBuilder(Value.Type.SCALAR).setScalar(value).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource(name, val));
    resBuilder.setRole("*");

    return resBuilder.build();
  }

  public static Resource getExpectedScalar(String name, double value) {
    Value val = new ValueBuilder(Value.Type.SCALAR).setScalar(value).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource(name, val));
    resBuilder.setRole(testRole);
    resBuilder.setReservation(getExpectedReservationInfo());

    return resBuilder.build();
  }

  private static DiskInfo getOfferedMountVolumeDiskInfo() {
    return DiskInfo.newBuilder()
      .setSource(Source.newBuilder()
          .setType(Source.Type.MOUNT)
          .setMount(Mount.newBuilder()
            .setRoot(testMountRoot)
            .build())
          .build())
      .build();
  }

  private static DiskInfo getExpectedMountVolumeDiskInfo() {
    return DiskInfo.newBuilder(getOfferedMountVolumeDiskInfo())
      .setPersistence(Persistence.newBuilder()
          .setId(testPersistenceId)
          .setPrincipal(testPrincipal)
          .build())
      .build();
  }

  private static DiskInfo getExpectedRootVolumeDiskInfo() {
    return DiskInfo.newBuilder()
      .setPersistence(Persistence.newBuilder()
          .setId(testPersistenceId)
          .setPrincipal(testPrincipal)
          .build())
      .build();
  }

  private static ReservationInfo getExpectedReservationInfo() {
    return ReservationInfo.newBuilder()
      .setPrincipal(testPrincipal)
      .setLabels(Labels.newBuilder()
          .addLabels(Label.newBuilder()
            .setKey(ResourceRequirement.RESOURCE_ID_KEY)
            .setValue(testResourceId)
            .build())
          .build())
      .build();
  }
}
