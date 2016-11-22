package com.mesosphere.sdk.scheduler.recovery.constrain;

import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.scheduler.recovery.RecoveryType;

/**
 * Implementation of {@link LaunchConstrainer} that always allows launches.
 * <p>
 * This is equivalent to disabling the launch constraining feature.
 */
public class UnconstrainedLaunchConstrainer implements LaunchConstrainer {
    @Override
    public void launchHappened(Operation launchOperation, RecoveryType recoveryType) {
        //do nothing
    }

    @Override
    public boolean canLaunch(RecoveryType recoveryType) {
        return true;
    }
}
