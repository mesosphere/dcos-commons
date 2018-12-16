package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code LAUNCH_GROUP} Operation.
 */
public class LaunchOfferRecommendation implements OfferRecommendation {

  private final Protos.Offer offer;

  private final Protos.Offer.Operation operation;

  public LaunchOfferRecommendation(
      Protos.Offer offer,
      Protos.TaskInfo taskInfo,
      Protos.ExecutorInfo executorInfo)
  {
    this.offer = offer;

    Protos.Offer.Operation.Builder builder = Protos.Offer.Operation.newBuilder();
    // For the LAUNCH_GROUP command, we put the ExecutorInfo in the operation, not the task itself.
    builder.setType(Protos.Offer.Operation.Type.LAUNCH_GROUP)
        .getLaunchGroupBuilder()
        .setExecutor(executorInfo)
        .getTaskGroupBuilder()
        .addTasks(taskInfo);
    this.operation = builder.build();
  }

  @Override
  public Optional<Protos.Offer.Operation> getOperation() {
    return Optional.of(operation);
  }

  public Protos.TaskInfo getTaskInfo() {
    return operation.getLaunchGroup().getTaskGroup().getTasks(0);
  }

  @Override
  public Protos.OfferID getOfferId() {
    return offer.getId();
  }

  @Override
  public Protos.SlaveID getAgentId() {
    return offer.getSlaveId();
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }
}
