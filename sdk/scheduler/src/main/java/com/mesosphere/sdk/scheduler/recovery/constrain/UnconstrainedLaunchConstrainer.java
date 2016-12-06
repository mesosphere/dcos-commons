package com.mesosphere.sdk.scheduler.recovery.constrain;

import org.apache.mesos.Protos.Offer.Operation;
import com.mesosphere.sdk.scheduler.recovery.RecoveryRequirement;

/**
 * Implementation of {@link LaunchConstrainer} that always allows launches.
 * <p>
 * This is equivalent to disabling the launch constraining feature.
 */
public class UnconstrainedLaunchConstrainer implements LaunchConstrainer {
    @Override
    public void launchHappened(Operation launchOperation, RecoveryRequirement.RecoveryType recoveryType) {
        //do nothing
    }

    @Override
    public boolean canLaunch(RecoveryRequirement recoveryRequirement) {
        return true;
    }
}
