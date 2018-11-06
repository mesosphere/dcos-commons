package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.StoreTaskInfoRecommendation;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;

import org.apache.mesos.Protos;

import java.util.Arrays;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class sets pod metadata on a {@link org.apache.mesos.Protos.TaskInfo}, ensuring
 * that this metadata is available in the task's environment and creating a {@link LaunchOfferRecommendation}.
 */
public class LaunchEvaluationStage implements OfferEvaluationStage {

  private final String serviceName;

  private final String taskSpecName;

  private final boolean shouldLaunch;

  /**
   * Creates a new evaluation stage for launching a task.
   *
   * @param serviceName  The name of the service that's being launched into
   * @param taskSpecName The name of the task (as from the TaskSpec)
   * @param shouldLaunch Whether a Launch recommendation should actually be emitted. If false, then the StateStore
   *                     will be updated with the resulting TaskInfo, but no launch will actually occur. This is used for updating
   *                     local information about tasks
   */
  public LaunchEvaluationStage(String serviceName, String taskSpecName, boolean shouldLaunch) {
    this.serviceName = serviceName;
    this.taskSpecName = taskSpecName;
    this.shouldLaunch = shouldLaunch;
  }

  @Override
  public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
    Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
    Protos.Offer offer = mesosResourcePool.getOffer();
    Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskSpecName);

    if (shouldLaunch) {
      taskBuilder.setTaskId(CommonIdUtils.toTaskId(serviceName, taskBuilder.getName()));
    } else {
      // This task is just being stored in ZK, not launched with Mesos (yet).
      taskBuilder.getTaskIdBuilder().setValue("");
    }
    taskBuilder.setSlaveId(offer.getSlaveId());

    // Store metadata in the TaskInfo for later access by placement constraints:
    TaskLabelWriter writer = new TaskLabelWriter(taskBuilder);
    writer.setOfferAttributes(offer)
        .setType(podInfoBuilder.getType())
        .setIndex(podInfoBuilder.getIndex())
        .setHostname(offer);
    if (offer.hasDomain() && offer.getDomain().hasFaultDomain()) {
      writer.setRegion(offer.getDomain().getFaultDomain().getRegion())
          .setZone(offer.getDomain().getFaultDomain().getZone());
    }

    taskBuilder.setLabels(writer.toProto());
    updateFaultDomainEnv(taskBuilder, offer);

    if (shouldLaunch) {
      return pass(
          this,
          // Launch (in Mesos) + Update (in our StateStore)
          Arrays.asList(
              new LaunchOfferRecommendation(offer, taskBuilder.build(), executorBuilder.build()),
              new StoreTaskInfoRecommendation(offer, taskBuilder.build(), executorBuilder.build())),
          String.format("Added launch operation for %s", taskSpecName))
          .build();
    } else {
      return pass(
          this,
          // Only update in StateStore. No launch in Mesos.
          Arrays.asList(new StoreTaskInfoRecommendation(offer, taskBuilder.build(), executorBuilder.build())),
          String.format("Added metadata update for %s", taskSpecName))
          .build();
    }
  }

  private static void updateFaultDomainEnv(Protos.TaskInfo.Builder builder, Protos.Offer offer) {
    if (!offer.hasDomain() || !offer.getDomain().hasFaultDomain() || !builder.hasCommand()) {
      return;
    }

    Protos.Environment.Variable regionVar = Protos.Environment.Variable.newBuilder()
        .setName(EnvConstants.REGION_TASKENV)
        .setValue(offer.getDomain().getFaultDomain().getRegion().getName())
        .build();

    Protos.Environment.Variable zoneVar = Protos.Environment.Variable.newBuilder()
        .setName(EnvConstants.ZONE_TASKENV)
        .setValue(offer.getDomain().getFaultDomain().getZone().getName())
        .build();

    builder.getCommandBuilder()
        .getEnvironmentBuilder()
        .addVariables(regionVar)
        .addVariables(zoneVar);
  }
}
