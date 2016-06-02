package org.apache.mesos.offer;

import java.util.Arrays;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.protobuf.OperationBuilder;

/**
 * Launch OfferRecommendation.
 * This Recommendation encapsulates a Mesos LAUNCH Operation
 */
public class LaunchOfferRecommendation implements OfferRecommendation {
  private OperationBuilder builder;
  private Offer offer;
  private TaskInfo taskInfo;
  private MesosTask mesosTask;

  public LaunchOfferRecommendation(Offer offer, TaskInfo taskInfo) {
    this.offer = offer;
    this.taskInfo = taskInfo;
    this.mesosTask = new MesosTask(taskInfo);

    builder = new OperationBuilder();
    builder.setType(Operation.Type.LAUNCH);
  }

  public Operation getOperation() {
    builder.setLaunch(Arrays.asList(
          TaskInfo.newBuilder(taskInfo)
          .setSlaveId(offer.getSlaveId())
          .build()));

    return builder.build();
  }

  public Offer getOffer() {
    return offer;
  }

  public boolean isTransient() {
    return mesosTask.isTransient();
  }
}
