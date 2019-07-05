package com.mesosphere.sdk.scheduler.plan.backoff;

import org.apache.mesos.Protos;

/**
 * Implementation of {@link BackOff} with zero backoff.
 */
public class DisabledBackOff extends BackOff {

  @Override
  public void addDelay(String taskInstanceName) {}

  @Override
  public void addDelay(Protos.TaskID taskID) {}

  @Override
  public boolean isReady(String taskInstanceName) {
    return false;
  }

  @Override
  public boolean clearDelay(String taskInstanceName) {
    return false;
  }

  @Override
  public boolean clearDelay(Protos.TaskID taskID) {
    return false;
  }
}
