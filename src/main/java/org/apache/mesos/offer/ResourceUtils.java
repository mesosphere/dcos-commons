package org.apache.mesos.offer;

import java.util.ArrayList;
import java.util.List;

import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;
import org.apache.mesos.Protos.Resource.ReservationInfo;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.Protos.Value.Range;
import org.apache.mesos.Protos.Value.Ranges;
import org.apache.mesos.Protos.Volume;

import org.apache.mesos.protobuf.LabelBuilder;
import org.apache.mesos.protobuf.ValueBuilder;

/**
 * This class encapsulates common methods for manipulating Resources.
 */
public class ResourceUtils {
  public static Resource getRawResource(String name, Value value) {
    return setResource(Resource.newBuilder(), name, value);
  }

  public static Resource getUnreservedMountVolume(double diskSize, String mountRoot) {
    Value diskValue = new ValueBuilder(Value.Type.SCALAR).setScalar(diskSize).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource("disk", diskValue));
    resBuilder.setRole("*");
    resBuilder.setDisk(getUnreservedMountVolumeDiskInfo(mountRoot));

    return resBuilder.build();
  }

  public static Resource getDesiredMountVolume(String role, String principal, double diskSize, String containerPath) {
    Value value = new ValueBuilder(Value.Type.SCALAR).setScalar(diskSize).build();
    Resource.Builder resBuilder = Resource.newBuilder(getRawResource("disk", value));
    resBuilder.setRole(role);
    resBuilder.setReservation(getDesiredReservationInfo(principal));
    resBuilder.setDisk(getDesiredMountVolumeDiskInfo(principal, containerPath));
    return resBuilder.build();
  }

  public static Resource getExpectedMountVolume(
          double diskSize,
          String resourceId,
          String role,
          String principal,
          String mountRoot,
          String persistenceId) {
    Value diskValue = new ValueBuilder(Value.Type.SCALAR).setScalar(diskSize).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource("disk", diskValue));
    resBuilder.setRole(role);
    resBuilder.setDisk(getExpectedMountVolumeDiskInfo(mountRoot, persistenceId, principal));
    resBuilder.setReservation(getExpectedReservationInfo(resourceId, principal));

    return resBuilder.build();
  }

  public static Resource getUnreservedRootVolume(double diskSize) {
    Value diskValue = new ValueBuilder(Value.Type.SCALAR).setScalar(diskSize).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource("disk", diskValue));
    resBuilder.setRole("*");
    return resBuilder.build();
  }

  public static Resource getDesiredRootVolume(String role, String principal, double diskSize, String containerPath) {
    Value value = new ValueBuilder(Value.Type.SCALAR).setScalar(diskSize).build();
    Resource.Builder resBuilder = Resource.newBuilder(getRawResource("disk", value));
    resBuilder.setRole(role);
    resBuilder.setReservation(getDesiredReservationInfo(principal));
    resBuilder.setDisk(getDesiredRootVolumeDiskInfo(principal, containerPath));
    return resBuilder.build();
  }

  public static Resource getExpectedRootVolume(
          double diskSize,
          String resourceId,
          String role,
          String principal,
          String persistenceId) {
    Value diskValue = new ValueBuilder(Value.Type.SCALAR).setScalar(diskSize).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource("disk", diskValue));
    resBuilder.setRole(role);
    resBuilder.setDisk(getExpectedRootVolumeDiskInfo(persistenceId, principal));
    resBuilder.setReservation(getExpectedReservationInfo(resourceId, principal));

    return resBuilder.build();
  }

  public static Resource getDesiredResource(String role, String principal, String name, Value value) {
    return Resource.newBuilder(getRawResource(name, value))
      .setRole(role)
      .setReservation(getDesiredReservationInfo(principal))
      .build();
  }

  public static Resource getUnreservedScalar(String name, double value) {
    Value val = new ValueBuilder(Value.Type.SCALAR).setScalar(value).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource(name, val));
    resBuilder.setRole("*");

    return resBuilder.build();
  }

  public static Resource getExpectedScalar(
          String name,
          double value,
          String resourceId,
          String role,
          String principal) {
    Value val = new ValueBuilder(Value.Type.SCALAR).setScalar(value).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource(name, val));
    resBuilder.setRole(role);
    resBuilder.setReservation(getExpectedReservationInfo(resourceId, principal));

    return resBuilder.build();
  }

  public static Resource getDesiredScalar(String role, String principal, String name, double value) {
    return getDesiredResource(role, principal, name, new ValueBuilder(Value.Type.SCALAR).setScalar(value).build());
  }

  public static Resource getUnreservedRanges(String name, List<Range> ranges) {
    Value val = new ValueBuilder(Value.Type.RANGES).setRanges(ranges).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource(name, val));
    resBuilder.setRole("*");

    return resBuilder.build();
  }

  public static Resource getDesiredRanges(String role, String principal, String name, List<Range> ranges) {
    return getDesiredResource(
        role,
        principal,
        name,
        Value.newBuilder()
        .setType(Value.Type.RANGES)
        .setRanges(Ranges.newBuilder()
          .addAllRange(ranges)
          .build())
        .build());
  }

  public static Resource getExpectedRanges(
          String name,
          List<Range> ranges,
          String resourceId,
          String role,
          String principal) {
    Value val = new ValueBuilder(Value.Type.RANGES).setRanges(ranges).build();
    Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getRawResource(name, val));
    resBuilder.setRole(role);
    resBuilder.setReservation(getExpectedReservationInfo(resourceId, principal));

    return resBuilder.build();
  }

  public static Resource setValue(Resource resource, Value value) {
    return setResource(Resource.newBuilder(resource), resource.getName(), value);
  }

  public static Resource setResourceId(Resource resource, String resourceId) {
    return Resource.newBuilder(resource)
      .setReservation(setResourceId(resource.getReservation(), resourceId))
      .build();
  }

  public static String getResourceId(Resource resource) {
    if (resource.hasReservation() && resource.getReservation().hasLabels()) {
      for (Label label : resource.getReservation().getLabels().getLabelsList()) {
        if (label.getKey().equals(MesosResource.RESOURCE_ID_KEY)) {
          return label.getValue();
        }
      }
    }
    return null;
  }

  public static String getPersistenceId(Resource resource) {
    if (resource.hasDisk() && resource.getDisk().hasPersistence()) {
      return resource.getDisk().getPersistence().getId();
    }

    return null;
  }

  public static TaskInfo clearResourceIds(TaskInfo taskInfo) {
    List<Resource> clearedTaskResources = clearResourceIds(taskInfo.getResourcesList());
    TaskInfo.Builder taskInfoBuilder = TaskInfo.newBuilder(taskInfo)
            .clearResources()
            .addAllResources(clearedTaskResources);

    if (taskInfo.hasExecutor()) {
      taskInfoBuilder.setExecutor(clearResourceIds(taskInfo.getExecutor()));
    }

    return taskInfoBuilder.build();
  }

  public static ExecutorInfo clearResourceIds(ExecutorInfo executorInfo) {
    List<Resource> clearedResources = clearResourceIds(executorInfo.getResourcesList());
    return ExecutorInfo.newBuilder(executorInfo)
            .clearResources()
            .addAllResources(clearedResources)
            .build();
  }

  private static List<Resource> clearResourceIds(List<Resource> resources) {
    List<Resource> clearedResources = new ArrayList<>();

    for (Resource resource : resources) {
      clearedResources.add(clearResourceId(resource));
    }

    return clearedResources;
  }

  private static Resource clearResourceId(Resource resource) {
    if (resource.hasReservation()) {
      List<Label> labels = resource.getReservation().getLabels().getLabelsList();

      Resource.Builder resourceBuilder = Resource.newBuilder(resource);
      Resource.ReservationInfo.Builder reservationBuilder = Resource.ReservationInfo
              .newBuilder(resource.getReservation());

      Labels.Builder labelsBuilder = Labels.newBuilder();
      for (Label label : labels) {
        if (!label.getKey().equals(MesosResource.RESOURCE_ID_KEY)) {
          labelsBuilder.addLabels(label);
        }
      }

      reservationBuilder.setLabels(labelsBuilder.build());
      resourceBuilder.setReservation(reservationBuilder.build());
      return resourceBuilder.build();
    } else {
      return resource;
    }
  }

  private static Resource setResource(Resource.Builder resBuilder, String name, Value value) {
    Value.Type type = value.getType();

    resBuilder
      .setName(name)
      .setType(type);

    switch (type) {
      case SCALAR:
        return resBuilder.setScalar(value.getScalar()).build();
      case RANGES:
        return resBuilder.setRanges(value.getRanges()).build();
      case SET:
        return resBuilder.setSet(value.getSet()).build();
      default:
        return null;
    }
  }

  private static ReservationInfo setResourceId(ReservationInfo resInfo, String resourceId) {
    return ReservationInfo.newBuilder(resInfo)
      .setLabels(setResourceId(resInfo.getLabels(), resourceId))
      .build();
  }

  public static Labels setResourceId(Labels labels, String resourceId) {
    LabelBuilder labelBuilder = new LabelBuilder();

    // Copy everything except blank resource ID label
    for (Label label : labels.getLabelsList()) {
      String key = label.getKey();
      String value = label.getValue();
      if (!key.equals(MesosResource.RESOURCE_ID_KEY)) {
        labelBuilder.addLabel(key, value);
      }
    }

    labelBuilder.addLabel(MesosResource.RESOURCE_ID_KEY, resourceId);

    return labelBuilder.build();
  }

  private static ReservationInfo getDesiredReservationInfo(String principal) {
    return getDesiredReservationInfo(principal, "");
  }

  private static ReservationInfo getDesiredReservationInfo(String principal, String reservationId) {
    return ReservationInfo.newBuilder()
      .setPrincipal(principal)
      .setLabels(getDesiredReservationLabels(reservationId))
      .build();
  }

  private static ReservationInfo getExpectedReservationInfo(String resourceId, String principal) {
    return ReservationInfo.newBuilder()
            .setPrincipal(principal)
            .setLabels(Labels.newBuilder()
                    .addLabels(Label.newBuilder()
                            .setKey(MesosResource.RESOURCE_ID_KEY)
                            .setValue(resourceId)
                            .build())
                    .build())
            .build();
  }

  private static Labels getDesiredReservationLabels(String resourceId) {
    return Labels.newBuilder()
      .addLabels(
          Label.newBuilder()
          .setKey(MesosResource.RESOURCE_ID_KEY)
          .setValue(resourceId)
          .build())
      .build();
  }

  private static DiskInfo getUnreservedMountVolumeDiskInfo(String mountRoot) {
    return DiskInfo.newBuilder()
            .setSource(Source.newBuilder()
                    .setType(Source.Type.MOUNT)
                    .setMount(Source.Mount.newBuilder()
                            .setRoot(mountRoot)
                            .build())
                    .build())
            .build();
  }

  private static DiskInfo getDesiredMountVolumeDiskInfo(String principal, String containerPath) {
    return DiskInfo.newBuilder()
      .setPersistence(Persistence.newBuilder()
          .setId("")
          .setPrincipal(principal)
          .build())
      .setSource(getDesiredMountVolumeSource())
      .setVolume(Volume.newBuilder()
          .setContainerPath(containerPath)
          .setMode(Volume.Mode.RW)
          .build())
      .build();
  }

  private static DiskInfo getExpectedMountVolumeDiskInfo(
          String mountRoot,
          String persistenceId,
          String principal) {
    return DiskInfo.newBuilder(getUnreservedMountVolumeDiskInfo(mountRoot))
            .setPersistence(Persistence.newBuilder()
                    .setId(persistenceId)
                    .setPrincipal(principal)
                    .build())
            .build();
  }

  private static DiskInfo getDesiredRootVolumeDiskInfo(String principal, String containerPath) {
    return DiskInfo.newBuilder()
      .setPersistence(Persistence.newBuilder()
          .setId("")
          .setPrincipal(principal)
          .build())
      .setVolume(Volume.newBuilder()
          .setContainerPath(containerPath)
          .setMode(Volume.Mode.RW)
          .build())
      .build();
  }

  private static DiskInfo getExpectedRootVolumeDiskInfo(String persistenceId, String principal) {
    return DiskInfo.newBuilder()
            .setPersistence(Persistence.newBuilder()
                    .setId(persistenceId)
                    .setPrincipal(principal)
                    .build())
            .build();
  }

  private static DiskInfo.Source getDesiredMountVolumeSource() {
    return Source.newBuilder().setType(Source.Type.MOUNT).build();
  }
}
