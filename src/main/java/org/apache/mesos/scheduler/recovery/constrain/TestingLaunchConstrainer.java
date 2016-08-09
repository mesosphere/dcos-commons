package org.apache.mesos.scheduler.recovery.constrain;

import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.offer.OfferRequirement;

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
    public void launchHappened(Operation launchOperation) {
        // Does nothing when the launch happens
    }

    @Override
    public boolean canLaunch(OfferRequirement offerRequirement) {
        return this.canLaunch;
    }
}
