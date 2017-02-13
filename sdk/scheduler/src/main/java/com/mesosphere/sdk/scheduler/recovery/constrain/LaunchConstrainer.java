package com.mesosphere.sdk.scheduler.recovery.constrain;

import org.apache.mesos.Protos.Offer.Operation;

import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;

/**
 * This interface provides methods which govern and react to Launch Operations.  It is sometimes desirable to limit the
 * rate of Launch Operations.  In particular may be desirable to limit the rate of Operations which may be destructive
 * in reaction to permanent failures.  Notifications regarding launch Operations and desired RecoveryRequirements allow
 * LaunchConstrainers to throttle Launch Operations.
 */
public interface LaunchConstrainer {
    /**
     * Invoked every time a task is launchHappened.
     * <p>
     * We take a {@link Operation} so that frameworks can specify additional metadata, in order to smooth the launch
     * rate.
     *
     * @param recommendation The OfferRecommendation containing the Launch Operation which occurred
     * @param recoveryType The type of the recovery which has been executed
     */
    void launchHappened(LaunchOfferRecommendation recommendation, RecoveryType recoveryType);

    /**
     * Determines whether the given {@link RecoveryType}
     * can be launchHappened right now.
     *
     * @param recoveryType The {@link RecoveryType} to be examined
     * @return {@code true} if the offer is safe to launch immediately, false if it should wait
     */
    boolean canLaunch(RecoveryType recoveryType);
}
