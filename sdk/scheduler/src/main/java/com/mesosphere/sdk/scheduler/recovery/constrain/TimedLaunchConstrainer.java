package com.mesosphere.sdk.scheduler.recovery.constrain;

import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;

import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements a {@link LaunchConstrainer} that requires a minimum number of seconds to elapse between launches, for
 * rate-limiting purposes.
 */
public class TimedLaunchConstrainer implements LaunchConstrainer {
  private final Logger logger = LoggingUtils.getLogger(getClass());

  private AtomicLong lastPermanentRecoveryLaunchMs;

  private Duration minDelay;

  /**
   * Create a new constrainer with the given required minimum delay between permanent (destructive) recovery
   * operations.
   *
   * @param minimumDelay Minimum delay between each destructive launch
   */
  public TimedLaunchConstrainer(Duration minimumDelay) {
    this.minDelay = minimumDelay;
    this.lastPermanentRecoveryLaunchMs = new AtomicLong(0);
  }

  @Override
  public void launchHappened(LaunchOfferRecommendation recommendation, RecoveryType recoveryType) {
    if (recoveryType.equals(RecoveryType.PERMANENT)) {
      lastPermanentRecoveryLaunchMs.compareAndSet(
          lastPermanentRecoveryLaunchMs.get(),
          System.currentTimeMillis());
    }
  }

  @Override
  public boolean canLaunch(RecoveryType recoveryType) {
    if (recoveryType.equals(RecoveryType.PERMANENT)) {
      long timeLeft =
          lastPermanentRecoveryLaunchMs.get() + minDelay.toMillis() - getCurrentTimeMs();
      if (timeLeft < 0) {
        return true;
      } else {
        logger.info(
            "Refusing to launch task for another {}s",
            TimeUnit.SECONDS.convert(timeLeft, TimeUnit.MILLISECONDS)
        );
        return false;
      }
    } else {
      return true;
    }
  }

  protected long getCurrentTimeMs() {
    return System.currentTimeMillis();
  }
}
