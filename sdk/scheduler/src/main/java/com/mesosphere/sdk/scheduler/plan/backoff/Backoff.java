package com.mesosphere.sdk.scheduler.plan.backoff;

import com.mesosphere.sdk.framework.EnvStore;
import com.mesosphere.sdk.offer.LoggingUtils;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Optional;

/**
 * Abstract class that has contracts on how delay can be set/advanced/cleared. Helper methods are used to
 * instantiate either a {@link ExponentialBackoff} or {@link DisabledBackoff} based on environment.
 */
public abstract class Backoff {
  private static final String FRAMEWORK_BACKOFF_FACTOR = "FRAMEWORK_BACKOFF_FACTOR";

  private static final String FRAMEWORK_INITIAL_BACKOFF = "FRAMEWORK_INITIAL_BACKOFF";

  private static final String FRAMEWORK_MAX_LAUNCH_DELAY = "FRAMEWORK_MAX_LAUNCH_DELAY";

  private static final String ENABLE_BACKOFF = "ENABLE_BACKOFF";

  private static final Object lock = new Object();

  private static Logger logger = LoggingUtils.getLogger(Backoff.class);

  private static volatile Backoff instance;

  /**
   * Backoff is enabled only by explicit Opt-In. We adhere to following configuration:
   *  * Set {@link #ENABLE_BACKOFF} to non blank value.
   *  * Set one or more of {@link #FRAMEWORK_BACKOFF_FACTOR} or
   *    {@link #FRAMEWORK_INITIAL_BACKOFF} and/or {@link #FRAMEWORK_MAX_LAUNCH_DELAY}.
   *    If more than zero params are initialized, defaults are used for the uninitialized params.
   *  * Absence of {@link #ENABLE_BACKOFF} and other environment params would be considered
   *    as hint to disable backoff
   */
  public static Backoff getInstance() {
    if (instance == null) {
      synchronized (lock) {
        if (instance == null) {
          EnvStore envStore = EnvStore.fromEnv();
          if (envStore.getOptionalBoolean(ENABLE_BACKOFF, false)) {
            // CHECKSTYLE:OFF MagicNumberCheck
            instance = new ExponentialBackoff(
                    envStore.getOptionalDouble(FRAMEWORK_BACKOFF_FACTOR, 1.15),
                    envStore.getOptionalLong(FRAMEWORK_INITIAL_BACKOFF, 60),
                    envStore.getOptionalLong(FRAMEWORK_MAX_LAUNCH_DELAY, 300)
            );
            // CHECKSTYLE:ON MagicNumberCheck
          } else {
            logger.warn("Disabling backoff as {} is not set", ENABLE_BACKOFF);
            instance = new DisabledBackoff();
          }
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
}
