package com.mesosphere.sdk.scheduler.plan.backoff;

import com.mesosphere.sdk.framework.EnvStore;
import com.mesosphere.sdk.offer.LoggingUtils;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

/**
 * Abstract class that has contracts on how delay can be set/advanced/cleared. Helper methods are used to
 * instantiate either a {@link ExponentialBackOff} or {@link DisabledBackOff} based on environment.
 */
public abstract class BackOff {
  private static final String FRAMEWORK_BACKOFF_FACTOR = "FRAMEWORK_BACKOFF_FACTOR";

  private static final String FRAMEWORK_INITIAL_BACKOFF = "FRAMEWORK_INITIAL_BACKOFF";

  private static final String FRAMEWORK_MAX_LAUNCH_DELAY = "FRAMEWORK_MAX_LAUNCH_DELAY";

  private static final String DISABLE_BACKOFF = "DISABLE_BACKOFF";

  private static final Object lock = new Object();

  private static Logger logger = LoggingUtils.getLogger(BackOff.class);

  private static volatile BackOff instance;

  public static BackOff getInstance() {
    if (instance == null) {
      synchronized (lock) {
        EnvStore envStore = EnvStore.fromEnv();
        if (envStore.isPresent(DISABLE_BACKOFF)) {
          logger.warn("Disabling backoff as {} is set to {}",
                  DISABLE_BACKOFF, envStore.getRequired(DISABLE_BACKOFF));
          instance = new DisabledBackOff();
        } else {
          // CHECKSTYLE:OFF MagicNumberCheck
          instance = new ExponentialBackOff(
                  envStore.getOptionalDouble(FRAMEWORK_BACKOFF_FACTOR, 1.15),
                  envStore.getOptionalLong(FRAMEWORK_INITIAL_BACKOFF, 60),
                  envStore.getOptionalLong(FRAMEWORK_MAX_LAUNCH_DELAY, 300)
          );
          // CHECKSTYLE:ON MagicNumberCheck
        }
      }
    }
    return instance;
  }

  public abstract void addDelay(String taskInstanceName);

  public abstract void addDelay(Protos.TaskID taskID);

  public abstract boolean isReady(String taskInstanceName);

  public abstract boolean clearDelay(String taskInstanceName);

  public abstract boolean clearDelay(Protos.TaskID taskID);
}
