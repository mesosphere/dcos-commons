package com.mesosphere.sdk.scheduler.plan.backoff;

import com.mesosphere.sdk.framework.EnvStore;
import com.mesosphere.sdk.offer.LoggingUtils;

import com.google.common.annotations.VisibleForTesting;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Optional;

/**
 * Abstract class that has contracts on how delay can be set/advanced/cleared. Helper methods are used to
 * instantiate either a {@link ExponentialBackOff} or {@link DisabledBackOff} based on environment.
 */
public abstract class BackOff {
  private static final String FRAMEWORK_BACKOFF_FACTOR = "FRAMEWORK_BACKOFF_FACTOR";

  private static final String FRAMEWORK_INITIAL_BACKOFF = "FRAMEWORK_INITIAL_BACKOFF";

  private static final String FRAMEWORK_MAX_LAUNCH_DELAY = "FRAMEWORK_MAX_LAUNCH_DELAY";

  private static final String ENABLE_BACKOFF = "ENABLE_BACKOFF";

  private static final Object lock = new Object();

  private static Logger logger = LoggingUtils.getLogger(BackOff.class);

  private static volatile BackOff instance;

  /**
   * Backoff is enabled only by explicit Opt-In. We adhere to following configuration:
   *  * Set {@link #ENABLE_BACKOFF} to non blank value.
   *  * Set one or more of {@link #FRAMEWORK_BACKOFF_FACTOR} or
   *    {@link #FRAMEWORK_INITIAL_BACKOFF} and/or {@link #FRAMEWORK_MAX_LAUNCH_DELAY}.
   *    If more than zero params are initialized, defaults are used for the uninitialized params.
   *  * Absence of {@link #ENABLE_BACKOFF} and other environment params would be considered
   *    as hint to disable backoff
   */
  public static BackOff getInstance() {
    if (instance == null) {
      synchronized (lock) {
        EnvStore envStore = EnvStore.fromEnv();
        if (envStore.isPresent(ENABLE_BACKOFF)
                || envStore.isPresent(FRAMEWORK_BACKOFF_FACTOR)
                || envStore.isPresent(FRAMEWORK_MAX_LAUNCH_DELAY)
                || envStore.isPresent(FRAMEWORK_INITIAL_BACKOFF))
        {
          // CHECKSTYLE:OFF MagicNumberCheck
          instance = new ExponentialBackOff(
                  envStore.getOptionalDouble(FRAMEWORK_BACKOFF_FACTOR, 1.15),
                  envStore.getOptionalLong(FRAMEWORK_INITIAL_BACKOFF, 60),
                  envStore.getOptionalLong(FRAMEWORK_MAX_LAUNCH_DELAY, 300)
          );
          // CHECKSTYLE:ON MagicNumberCheck
        } else {
          logger.warn("Disabling backoff");
          instance = new DisabledBackOff();
        }
      }
    }
    return instance;
  }

  public abstract void addDelay(String taskInstanceName);

  public abstract void addDelay(Protos.TaskID taskID);

  public abstract Optional<Duration> getDelay(String taskInstanceName);

  public abstract boolean clearDelay(String taskInstanceName);

  public abstract boolean clearDelay(Protos.TaskID taskID);

  @VisibleForTesting
  final void reset() {
    synchronized (lock) {
      instance = null;
    }
  }
}
