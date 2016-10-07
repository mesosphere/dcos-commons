package org.apache.mesos.scheduler.recovery.constrain;

import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.scheduler.recovery.RecoveryRequirement;
import org.apache.mesos.scheduler.recovery.RecoveryRequirementUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements a {@link LaunchConstrainer} that requires a minimum number of seconds to elapse between launches, for
 * rate-limiting purposes.
 */
public class TimedLaunchConstrainer implements LaunchConstrainer {
    private final Logger logger = LoggerFactory.getLogger(getClass());

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
    public void launchHappened(Operation launchOperation, RecoveryRequirement.RecoveryType recoveryType) {
        if (RecoveryRequirementUtils.isPermanent(recoveryType)) {
            lastPermanentRecoveryLaunchMs.compareAndSet(
                    lastPermanentRecoveryLaunchMs.get(),
                    System.currentTimeMillis());
        }
    }

    @Override
    public boolean canLaunch(RecoveryRequirement recoveryRequirement) {
        if (RecoveryRequirementUtils.isPermanent(recoveryRequirement)) {
            Long timeLeft = lastPermanentRecoveryLaunchMs.get() + minDelay.toMillis() - getCurrentTimeMs();
            if (timeLeft < 0) {
                return true;
            } else {
                Long secondsLeft = (long) Math.ceil(timeLeft / 1000.0);
                logger.info("Refusing to launch task for another " + secondsLeft + "s.");
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
