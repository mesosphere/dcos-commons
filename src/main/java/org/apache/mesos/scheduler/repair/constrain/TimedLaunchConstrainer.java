package org.apache.mesos.scheduler.repair.constrain;

import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.offer.OfferRequirement;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements a {@link LaunchConstrainer} that requires a minimum number of seconds to elapse between launches, for
 * rate-limiting purposes.
 */
public class TimedLaunchConstrainer implements LaunchConstrainer {
    private AtomicLong lastLaunch;
    private long minDelaySeconds;

    /**
     * Create a new constrainer with the given required minimum delay between recovery operations.
     *
     * @param minDelaySeconds Minimum number of seconds between each launch
     */
    public TimedLaunchConstrainer(long minDelaySeconds) {
        this.minDelaySeconds = minDelaySeconds;
        this.lastLaunch = new AtomicLong(0);
    }

    @Override
    public void launchHappened(Operation launchOperation) {
        if (!canLaunch(null)) {
            throw new RuntimeException("It hasn't been long enough since the last launch");
        }
        lastLaunch.compareAndSet(lastLaunch.get(), System.currentTimeMillis());
    }

    @Override
    public boolean canLaunch(OfferRequirement offerRequirement) {
        return (lastLaunch.get() + minDelaySeconds * 1000) < System.currentTimeMillis();
    }
}
