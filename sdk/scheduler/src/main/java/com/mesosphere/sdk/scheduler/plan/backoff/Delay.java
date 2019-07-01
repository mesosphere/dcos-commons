package com.mesosphere.sdk.scheduler.plan.backoff;

import java.time.Duration;

/**
 * TODO.
 */
public class Delay {

  private long referenceTimestamp;

  private Duration currentDelay;

  private final Duration maxLaunchDelay;

  private final double backoffFactor;

  Delay(Duration currentDelay, Duration maxLaunchDelay, double backOffFactor) {
    this.referenceTimestamp = System.currentTimeMillis();
    this.currentDelay = currentDelay;
    this.maxLaunchDelay = maxLaunchDelay;
    this.backoffFactor = backOffFactor;
  }

  public void advanceDelay() {
    Duration newDelay = Duration.ofNanos((long) (currentDelay.toNanos() * backoffFactor));
    if (newDelay.compareTo(maxLaunchDelay) > 0) {
      newDelay = maxLaunchDelay;
    }
    referenceTimestamp = System.currentTimeMillis();
    currentDelay = newDelay;
  }

  public Duration getCurrentDelay() {
    return currentDelay;
  }

  public boolean isOver() {
    return referenceTimestamp + currentDelay.toMillis() < System.currentTimeMillis();
  }

  public Duration expiresIn() {
    return Duration.ofMillis(
            currentDelay.toMillis() - (System.currentTimeMillis() - referenceTimestamp));
  }

  @Override
  public String toString() {
    return String.format("Current delay of %d second(s), expires in %d",
            currentDelay.getSeconds(),
            expiresIn().getSeconds());
  }
}
