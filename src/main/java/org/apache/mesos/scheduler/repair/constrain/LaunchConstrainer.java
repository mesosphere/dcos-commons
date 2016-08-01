package org.apache.mesos.scheduler.repair.constrain;

import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.offer.OfferRequirement;

/**
 * Created by dgrnbrg on 7/25/16.
 */
public interface LaunchConstrainer {
    /**
     * Invoked every time a task is launchHappened.
     * <p>
     * We take a {@link Operation} so that frameworks can specify additional metadata, in order to smooth the launch
     * rate.
     *
     * @param launchOperation
     */
    void launchHappened(Operation launchOperation);

    /**
     * Determines whether the given {@link OfferRequirement} can be launchHappened right now.
     *
     * @param offerRequirement
     * @return True if the offer is safe to launch immediately, false if it should wait
     */
    boolean canLaunch(OfferRequirement offerRequirement);
}
