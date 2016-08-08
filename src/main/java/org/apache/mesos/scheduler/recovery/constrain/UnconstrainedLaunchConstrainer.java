package org.apache.mesos.scheduler.recovery.constrain;

import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.offer.OfferRequirement;

/**
 * Implementation of {@link LaunchConstrainer} that always allows launches.
 * <p>
 * This is equivalent to disabling the launch constraining feature.
 */
public class UnconstrainedLaunchConstrainer implements LaunchConstrainer {
    @Override
    public void launchHappened(Operation launchOperation) {
        //do nothing
    }

    @Override
    public boolean canLaunch(OfferRequirement offerRequirement) {
        return true;
    }
}
