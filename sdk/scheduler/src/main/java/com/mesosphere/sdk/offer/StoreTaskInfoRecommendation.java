package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * A recommendation to update a {@code TaskInfo} in the {@code StateStore} alongside any operations to be performed
 * against Mesos. For example, this can be used to update our internal state of a task when its reservations or
 * configuration are modified.
 * <p>
 * This is intentionally passed around as an {@link OfferRecommendation} to ensure that it's (only) performed following
 * a successful evaluation, alongside sending the Mesos operations.
 */
public class StoreTaskInfoRecommendation implements OfferRecommendation {

  private final Protos.Offer offer;

  private final Protos.TaskInfo taskInfo;

  private final Protos.ExecutorInfo executorInfo;

  public StoreTaskInfoRecommendation(
      Protos.Offer offer,
      Protos.TaskInfo taskInfo,
      Protos.ExecutorInfo executorInfo)
  {
    this.offer = offer;
    this.taskInfo = taskInfo;
    this.executorInfo = executorInfo;
  }

  @Override
  public Optional<Protos.Offer.Operation> getOperation() {
    // Nothing to send to Mesos. Just update our own StateStore.
    return Optional.empty();
  }

  @Override
  public Protos.OfferID getOfferId() {
    return offer.getId();
  }

  @Override
  public Protos.SlaveID getAgentId() {
    return offer.getSlaveId();
  }

  /**
   * Returns the {@link Protos.TaskInfo} to be passed to a StateStore upon launch. The TaskInfo is formatted
   * differently depending on where it's being sent:
   * <p>
   * <ul><li>Mesos {@code LAUNCH_GROUP}: The {@link Protos.ExecutorInfo} is included in the
   * {@link Protos.Offer.Operation}, but not in the {@link Protos.TaskInfo}.</li>
   * <li>StateStore: The {@link Protos.ExecutorInfo} is included in the {@link Protos.TaskInfo} directly. This is how
   * tasks used to be launched in Mesos' classic {@code LAUNCH} operations. We now store there so that it can be
   * fetched when launching other tasks against the same executor instance</li></ul>
   */
  public Protos.TaskInfo getStateStoreTaskInfo() {
    return taskInfo.toBuilder()
        .setExecutor(executorInfo)
        .build();
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }
}
