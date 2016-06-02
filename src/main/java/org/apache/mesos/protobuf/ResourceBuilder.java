package org.apache.mesos.protobuf;

import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;
import org.apache.mesos.Protos.Resource.ReservationInfo;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.Protos.Value.Range;
import org.apache.mesos.Protos.Volume;
import org.apache.mesos.offer.ResourceRequirement;

import java.util.List;

/**
 * Builder class for working with protobufs.  It includes 2 different approaches;
 * 1) static functions useful for developers that want helpful protobuf functions for Resource.
 * 2) builder class
 * All builder classes provide access to the protobuf builder for capabilities beyond the included
 * helpful functions.
 * <p/>
 * This builds Resource objects and provides some convenience functions for common resources.
 **/

public class ResourceBuilder {
  private String role;
  private static final String DEFAULT_ROLE = "*";

  public ResourceBuilder(String role) {
    this.role = role;
  }

  public ResourceBuilder() {
    this(DEFAULT_ROLE);
  }

  public Resource createCpuResource(double value) {
    return cpus(value, role);
  }

  public Resource createMemResource(double value) {
    return mem(value, role);
  }

  public Resource createDiskResource(double value) {
    return disk(value, role);
  }

  public Resource createPortResource(long begin, long end) {
    return ports(begin, end, role);
  }

  public Resource createScalarResource(String name, double value) {
    return ResourceBuilder.createScalarResource(name, value, role);
  }

  public Resource createRangeResource(String name, long begin, long end) {
    return ResourceBuilder.createRangeResource(name, begin, end, role);
  }

  public static Resource getResource(String name, Value value) {
    Resource.Builder builder = Resource.newBuilder();
    Value.Type type = value.getType();
    builder.setName(name);
    builder.setType(type);
    builder.setRole("*");

    switch (type) {
      case SCALAR:
        return builder.setScalar(value.getScalar()).build();
      case RANGES:
        return builder.setRanges(value.getRanges()).build();
      case SET:
        return builder.setSet(value.getSet()).build();
      default:
        return null;
    }
  }

  public static Resource createScalarResource(String name, double value, String role) {
    return Resource.newBuilder()
      .setName(name)
      .setType(Value.Type.SCALAR)
      .setScalar(Value.Scalar.newBuilder().setValue(value).build())
      .setRole(role)
      .build();
  }

  public static Resource createRangeResource(String name, long begin, long end, String role) {
    Value.Range range = Value.Range.newBuilder().setBegin(begin).setEnd(end).build();
    return Resource.newBuilder()
      .setName(name)
      .setType(Value.Type.RANGES)
      .setRanges(Value.Ranges.newBuilder().addRange(range))
      .setRole(role)
      .build();
  }

  public static Resource createRangeResource(String name, List<Range> ranges, String role) {
    return Resource.newBuilder()
      .setName(name)
      .setType(Value.Type.RANGES)
      .setRanges(Value.Ranges.newBuilder().addAllRange(ranges))
      .setRole(role)
      .build();
  }

  public static Resource addDiskInfo(Resource diskRes, Resource.DiskInfo diskInfo) {
    return Resource.newBuilder(diskRes)
      .setDisk(diskInfo)
      .build();
  }

  public static Resource reservedCpus(double value, String role, String principal) {
    return reservedCpus(value, role, principal, "");
  }

  public static Resource reservedCpus(double value, String role, String principal, String resourceId) {
    Resource cpuRes = cpus(value, role);
    return addReservation(cpuRes, role, principal, resourceId);
  }

  public static Resource cpus(double value) {
    return cpus(value, DEFAULT_ROLE);
  }

  public static Resource cpus(double value, String role) {
    return createScalarResource("cpus", value, role);
  }

  public static Resource reservedMem(double value, String role, String principal) {
    return reservedMem(value, role, principal, "");
  }

  public static Resource reservedMem(double value, String role, String principal, String resourceId) {
    Resource memRes = mem(value, role);
    return addReservation(memRes, role, principal, resourceId);
  }

  public static Resource mem(double value) {
    return mem(value, DEFAULT_ROLE);
  }

  public static Resource mem(double value, String role) {
    return createScalarResource("mem", value, role);
  }

  public static Resource reservedDisk(double value, String role, String principal) {
    Resource diskRes = disk(value, role);
    return addReservation(diskRes, role, principal, "");
  }

  public static Resource reservedDisk(double value, String role, String principal, String resourceId) {
    Resource diskRes = disk(value, role);
    return addReservation(diskRes, role, principal, resourceId);
  }

  public static Resource disk(double sizeInMB) {
    return disk(sizeInMB, DEFAULT_ROLE);
  }

  public static Resource disk(double sizeInMB, String role) {
    return createScalarResource("disk", sizeInMB, role);
  }

  public static Resource reservedPorts(long begin, long end, String role, String principal) {
    return reservedPorts(begin, end, role, principal, "");
  }

  public static Resource reservedPorts(long begin, long end, String role, String principal, String resourceId) {
    Resource portsRes = ports(begin, end, role);
    return addReservation(portsRes, role, principal, resourceId);
  }

  public static Resource reservedPorts(List<Range> ports, String role, String principal) {
    return reservedPorts(ports, role, principal, "");
  }

  public static Resource reservedPorts(List<Range> ports, String role, String principal, String resourceId) {
    Resource portRes = ports(ports, role);
    return addReservation(portRes, role, principal, resourceId);
  }

  public static Resource ports(long begin, long end, String role) {
    return createRangeResource("ports", begin, end, role);
  }

  public static Resource ports(long begin, long end) {
    return ports(begin, end, DEFAULT_ROLE);
  }

  public static Resource ports(List<Range> ports, String role) {
    return createRangeResource("ports", ports, role);
  }

  public static Resource volume(double disk, String role, String principal, String containerPath) {
    return volume(disk, role, principal, containerPath, "");
  }

  public static Resource mountVolume(double disk, String role, String principal, String containerPath) {
    Resource vol = volume(disk, role, principal, containerPath, "");
    Resource.Builder resBuilder = Resource.newBuilder(vol);
    DiskInfo.Builder diskBuilder = DiskInfo.newBuilder(vol.getDisk());
    diskBuilder.clearPersistence();
    Source.Builder sourceBuilder = Source.newBuilder();
    sourceBuilder.setType(Source.Type.MOUNT);

    diskBuilder.setSource(sourceBuilder.build());
    resBuilder.setDisk(diskBuilder.build());
    return resBuilder.build();
  }

  public static Resource mountVolume(double diskSize, String root) {
    Resource diskResource = disk(diskSize);
    DiskInfo diskInfo = DiskInfo.newBuilder()
      .setSource(
          Source.newBuilder()
          .setType(Source.Type.MOUNT)
          .setMount(
            Source.Mount.newBuilder()
            .setRoot(root).build())
          .build())
      .build();

    return Resource.newBuilder(diskResource).setDisk(diskInfo).build();
  }

  public static Resource volume(
      double disk,
      String role,
      String principal,
      String containerPath,
      String persistenceId) {

    DiskInfo diskInfo = disk(persistenceId, containerPath);

    String resourceId = null;

    if (!persistenceId.isEmpty()) {
      resourceId = persistenceId;
    } else {
      resourceId = "";
    }

    Resource resource = reservedDisk(disk, role, principal, resourceId);
    return addDiskInfo(resource, diskInfo);
  }

  private static DiskInfo disk(String persistenceId, String containerPath) {
    return DiskInfo.newBuilder()
      .setPersistence(Persistence.newBuilder()
          .setId(persistenceId))
      .setVolume(Volume.newBuilder()
          .setMode(Volume.Mode.RW)
          .setContainerPath(containerPath))
      .build();
  }

  private static Resource addReservation(Resource resource, String role, String principal, String resourceId) {
    return Resource.newBuilder(resource)
      .setReservation(ReservationInfo.newBuilder()
          .setPrincipal(principal)
          .setLabels(new LabelBuilder()
            .addLabel(ResourceRequirement.RESOURCE_ID_KEY, resourceId).build()))
      .build();
  }
}
