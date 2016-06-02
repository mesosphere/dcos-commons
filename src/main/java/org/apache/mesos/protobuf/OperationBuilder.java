package org.apache.mesos.protobuf;

import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Offer.Operation.Create;
import org.apache.mesos.Protos.Offer.Operation.Destroy;
import org.apache.mesos.Protos.Offer.Operation.Launch;
import org.apache.mesos.Protos.Offer.Operation.Reserve;
import org.apache.mesos.Protos.Offer.Operation.Unreserve;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

import java.util.Collection;
import java.util.List;

/**
 * 1) static functions useful for developers that want helpful protobuf functions for Operation.
 * 2) builder class
 * All builder classes provide access to the protobuf builder for capabilities beyond the included
 * helpful functions.
 * <p/>
 * This builds Operation objects.
 */
public class OperationBuilder {
  private Operation.Builder builder = Operation.newBuilder();

  public OperationBuilder setType(Operation.Type type) {
    builder.setType(type);
    return this;
  }

  public OperationBuilder setReserve(List<Resource> resources) {
    Reserve reserve = getReserve(resources);
    builder.setReserve(reserve);
    return this;
  }

  public OperationBuilder setUnreserve(List<Resource> resources) {
    Unreserve unreserve = getUnreserve(resources);
    builder.setUnreserve(unreserve);
    return this;
  }

  public OperationBuilder setCreate(List<Resource> volumes) {
    Create create = getCreate(volumes);
    builder.setCreate(create);
    return this;
  }

  public OperationBuilder setDestroy(List<Resource> volumes) {
    Destroy destroy = getDestroy(volumes);
    builder.setDestroy(destroy);
    return this;
  }

  public OperationBuilder setLaunch(Collection<TaskInfo> taskInfos) {
    Launch launch = getLaunch(taskInfos);
    builder.setLaunch(launch);
    return this;
  }

  private Reserve getReserve(List<Resource> resources) {
    Reserve.Builder builder = Reserve.newBuilder();
    builder.addAllResources(resources);
    return builder.build();
  }

  private Unreserve getUnreserve(List<Resource> resources) {
    Unreserve.Builder builder = Unreserve.newBuilder();
    builder.addAllResources(resources);
    return builder.build();
  }

  private Create getCreate(List<Resource> volumes) {
    Create.Builder builder = Create.newBuilder();
    builder.addAllVolumes(volumes);
    return builder.build();
  }

  private Destroy getDestroy(List<Resource> volumes) {
    Destroy.Builder builder = Destroy.newBuilder();
    builder.addAllVolumes(volumes);
    return builder.build();
  }

  private Launch getLaunch(Collection<TaskInfo> taskInfos) {
    Launch.Builder builder = Launch.newBuilder();
    builder.addAllTaskInfos(taskInfos);
    return builder.build();
  }

  public Operation build() {
    return builder.build();
  }

  public Operation.Builder builder() {
    return builder;
  }
}
