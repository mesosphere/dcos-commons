package com.mesosphere.sdk.scheduler.recovery.constrain;

import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;

/**
 * Implementation of {@link LaunchConstrainer} that always allows launches.
 * <p>
 * This is equivalent to disabling the launch constraining feature.
 */
public class UnconstrainedLaunchConstrainer implements LaunchConstrainer {
    @Override
    public void launchHappened(LaunchOfferRecommendation recommendation, RecoveryType recoveryType) {
        //do nothing
    }

    @Override
    public boolean canLaunch(RecoveryType recoveryType) {
        return true;
    }
}
