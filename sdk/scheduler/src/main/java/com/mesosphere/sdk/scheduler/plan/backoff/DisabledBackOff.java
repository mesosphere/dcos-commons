package com.mesosphere.sdk.scheduler.plan.backoff;

import org.apache.mesos.Protos;

import java.time.Duration;
import java.util.Optional;

/**
 * Implementation of {@link BackOff} with zero backoff.
 */
public class DisabledBackOff extends BackOff {

  @Override
  public void addDelay(String taskInstanceName) {}

  @Override
  public void addDelay(Protos.TaskID taskID) {}

  @Override
  public Optional<Duration> getDelay(String taskInstanceName) {
    return Optional.empty();
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
