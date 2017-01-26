package com.mesosphere.sdk.scheduler.recovery.constrain;

import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;

/**
 * {@link LaunchConstrainer} that makes it easy to enable/disable launches for testing.
 * <p>
 * Defaults to allowing all launches.
 */
public class TestingLaunchConstrainer implements LaunchConstrainer {
    private boolean canLaunch;

    public TestingLaunchConstrainer() {
        this.canLaunch = false;
    }

    public void setCanLaunch(boolean canLaunch) {
        this.canLaunch = canLaunch;
    }

    @Override
    public void launchHappened(LaunchOfferRecommendation recommendation, RecoveryType recoveryType) {
        // Does nothing when the launch happens
    }

    @Override
    public boolean canLaunch(RecoveryType recoveryType) {
        return this.canLaunch;
    }
}
