package com.mesosphere.sdk.scheduler.plan.backoff;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskException;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO.
 */
public final class ExponentialBackOff {

  private static final Object lock = new Object();

  private static volatile ExponentialBackOff instance;

  private ConcurrentHashMap<String, Delay> delays;

  private double backoffFactor;

  private Duration initialBackoff;

  private Duration maxLaunchDelay;

  private Logger logger;

  private ExponentialBackOff(
          double backoffFactor,
          long backoffSeconds,
          long maxLaunchDelaySeconds)
  {
    this.logger = LoggingUtils.getLogger(ExponentialBackOff.class);
    this.backoffFactor = backoffFactor;
    this.initialBackoff = Duration.ofSeconds(backoffSeconds);
    this.maxLaunchDelay = Duration.ofSeconds(maxLaunchDelaySeconds);
    delays = new ConcurrentHashMap<>();
  }

  public static ExponentialBackOff getInstance() {
    if (instance == null) {
      synchronized (lock) {
        if (instance == null) {
          // SUPPRESS CHECKSTYLE MagicNumberCheck
          instance = new ExponentialBackOff(1.5, 60, 300);
        }
      }
    }
    return instance;
  }

  public Delay getDelay(String taskName) {
    return delays.get(taskName);
  }

  public void addDelay(Protos.TaskID taskId) {
    try {
      addDelay(CommonIdUtils.toTaskName(taskId));
    } catch (TaskException e) {
      //TODO
    }
  }

  public void addDelay(String taskName) {
    Delay delay = delays.get(taskName);
    if (delay == null) {
      logger.info("Creating delay for {} with {}", taskName, initialBackoff.toString());
      delay = new Delay(initialBackoff, maxLaunchDelay, backoffFactor);
    } else {
      logger.info("Current delay for {} is {}", taskName, delay.getCurrentDelay().toString());
      delay.advanceDelay();
      logger.info("Advancing existing delay for {} to {}",
              taskName, delay.getCurrentDelay().toString());
    }
    delays.put(taskName, delay);
  }

  public boolean clearDelay(Protos.TaskID taskId) {
    try {
      clearDelay(CommonIdUtils.toTaskName(taskId));
    } catch (TaskException e) {
      //TODO
    }
    return false;
  }

  public boolean clearDelay(String taskName) {
    if (delays.remove(taskName) != null) {
      logger.info("Cleared delay for {}", taskName);
      return true;
    } else {
      logger.info("No delay set for {}", taskName);
      return false;
    }
  }

}
