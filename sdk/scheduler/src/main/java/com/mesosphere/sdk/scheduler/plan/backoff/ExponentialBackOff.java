package com.mesosphere.sdk.scheduler.plan.backoff;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskException;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link BackOff} with back off increasing exponentially.
 * This is an example of how this works:
 * event 0: Task A is seen by scheduler for the first time ever.
 *          No back off. Launched on the first offer match.
 * event 1: Task A has failed and mesos sends us a status update.
 *          Backoff is created with value of {@link ExponentialBackOff#initialBackoff}.
 *          Task launches after this duration is elapsed.
 * event 2: Task A is re-launched after the backoff elapse. Task A fails again and mesos sends an update.
 * event 3: Task A is backed off with new duration of
 *            {@link ExponentialBackOff#initialBackoff} * {@link ExponentialBackOff#backoffFactor}
 *          The above value is capped at max value of {@link ExponentialBackOff#maxLaunchDelay}
 * event 4: steps 2 and 3 above are repeated in a loop.
 */
public final class ExponentialBackOff extends BackOff {

  private ConcurrentHashMap<String, Delay> delays;

  private double backoffFactor;

  private Duration initialBackoff;

  private Duration maxLaunchDelay;

  private Logger logger;

  ExponentialBackOff(double backoffFactor, long backoffSeconds, long maxLaunchDelaySeconds) {
    this.logger = LoggingUtils.getLogger(ExponentialBackOff.class);
    this.backoffFactor = backoffFactor;
    this.initialBackoff = Duration.ofSeconds(backoffSeconds);
    this.maxLaunchDelay = Duration.ofSeconds(maxLaunchDelaySeconds);
    delays = new ConcurrentHashMap<>();
    logger.info("Instantiating exponential back off with \n" +
            "backOffFactor : {}, initialBackOff : {} and maxLaunchDelay : {} ",
            backoffFactor, backoffSeconds, maxLaunchDelaySeconds);
  }

  public boolean isReady(String taskName) {
    Delay delay = delays.get(taskName);
    logger.debug("Delay for task [{}] is {}", taskName, delay);
    return delay == null || delay.isOver();
  }

  public void addDelay(String taskName) {
    Delay delay = delays.get(taskName);
    if (delay == null) {
      logger.debug("Creating delay for {} with {}", taskName, initialBackoff.getSeconds());
      delay = new Delay(initialBackoff, maxLaunchDelay, backoffFactor);
    } else {
      delay.advanceDelay();
      logger.debug("Advancing existing delay for {} to {}",
              taskName, delay.getCurrentDelay().getSeconds());
    }
    delays.put(taskName, delay);
  }

  @Override
  public void addDelay(Protos.TaskID taskID) {
    try {
      addDelay(CommonIdUtils.toTaskName(taskID));
    } catch (TaskException te) {
      logger.error("Unable to extract taskName from {} to add delay", taskID, te);
    }
  }

  public boolean clearDelay(String taskName) {
    if (delays.remove(taskName) != null) {
      logger.debug("Cleared delay for {}", taskName);
      return true;
    } else {
      logger.debug("No delay set for {}", taskName);
      return false;
    }
  }

  @Override
  public boolean clearDelay(Protos.TaskID taskID) {
    try {
      return clearDelay(CommonIdUtils.toTaskName(taskID));
    } catch (TaskException te) {
      logger.error("Unable to extract taskName from {} to clear delay", taskID, te);
      return false;
    }
  }

  private static class Delay {

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

    void advanceDelay() {
      Duration newDelay = Duration.ofNanos((long) (currentDelay.toNanos() * backoffFactor));
      if (newDelay.compareTo(maxLaunchDelay) > 0) {
        newDelay = maxLaunchDelay;
      }
      referenceTimestamp = System.currentTimeMillis();
      currentDelay = newDelay;
    }

    Duration getCurrentDelay() {
      return currentDelay;
    }

    boolean isOver() {
      return referenceTimestamp + currentDelay.toMillis() < System.currentTimeMillis();
    }
  }
}
