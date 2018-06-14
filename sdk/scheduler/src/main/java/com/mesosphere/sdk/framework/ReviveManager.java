package com.mesosphere.sdk.framework;

import java.util.Optional;

import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.Metrics;

/**
 * Handles scheduling of Mesos suppress and revive calls.
 *
 * <ul>
 * <li>Suppress is performed whenever the underlying services are all idle and no further offers are needed. This allows
 * Mesos to scale to more frameworks. When the framework is suppressed, it will not receive any offers for any reason.
 * </li>
 * <li>Revive will reset any prior Suppress call, and/or reset any offers which were previously declined. It should be
 * invoked when any new work has appeared, but Revive calls should be rate limited to avoid taxing Mesos.</li>
 * </ul>
 */
class ReviveManager {

    private static final Logger LOGGER = LoggingUtils.getLogger(ReviveManager.class);

    // Rate limiter for revive calls.
    private TokenBucket reviveTokenBucket;
    // Whether we have had new work appear since the last time revive was called:
    private boolean reviveRequested;
    // Whether we think that we have suppressed offers from Mesos due to an idle state on our end:
    private boolean isSuppressed;

    ReviveManager(TokenBucket reviveTokenBucket) {
        this.reviveTokenBucket = reviveTokenBucket;
        this.reviveRequested = false;
        this.isSuppressed = false;
    }

    /**
     * Notifies the manager that we are no longer suppressed. This should be called whenever offers are received from
     * Mesos. This confirmation is done separately from the revive call to avoid the possibility of the following
     * scenario:
     *
     * <ol>
     * <li>Offers are suppressed</li>
     * <li>A revive call is issued</li>
     * <li>The revive call fails or is lost for any reason</li>
     * <li>We continue to be suppressed, even though we think we issued a revive...</li>
     * </ol>
     *
     * In practice, there isn't a confirmed case of this ever happening, but it doesn't hurt to be conservative here,
     * because we cannot deterministically tell if we are actually suppressed or not. This logic could be revisited if
     * Mesos someday offers a call which tells us whether or not we're suppressed.
     */
    synchronized void notifyOffersReceived() {
        isSuppressed = false;
        Metrics.notSuppressed();
    }

    /**
     * Notifies the manager that one or more managed services are not idle.
     * If offers are suppressed, then this schedules a revive.
     */
    synchronized void notifyOffersNeeded(boolean needsOffers) {
        if (needsOffers) {
            if (isSuppressed) {
                // Service needs offers, but offers are suppressed. Schedule a revive.
                requestRevive();
            }
        } else {
            if (isSuppressed) {
                // Service doesn't need offers, but offers are already suppressed. Avoid duplicate suppress call.
                return;
            }

            // Service doesn't need offers, and offers are not suppressed. Suppress.
            LOGGER.info("Suppressing offers");
            Optional<SchedulerDriver> driver = Driver.getDriver();
            if (!driver.isPresent()) {
                throw new IllegalStateException("INTERNAL ERROR: No driver present for suppressing offers");
            }
            driver.get().suppressOffers();

            isSuppressed = true;
            Metrics.incrementSuppresses();
        }
    }

    /**
     * Notifies the manager that a revive should be sent soon. This is needed in either of the following cases:
     * <ul>
     * <li>The service has new work to do and any previously declined offers should be sent again</li>
     * <li>Offers are suppressed but the service is not idle (via {@link #notifyOffersNeeded(boolean)}</li>
     * </ul>
     */
    synchronized void requestRevive() {
        reviveRequested = true;
    }

    /**
     * Pings the manager to perform a revive call to Mesos if one was previously requested. This must be invoked
     * periodically to trigger revives.
     */
    synchronized void reviveIfRequested() {
        if (!reviveRequested) {
            // No revive is requested (either via requestRevive() or via notifyNotIdle()) -- no work needed.
            return;
        }

        if (!reviveTokenBucket.tryAcquire()) {
            LOGGER.info("Revive attempt has been throttled");
            Metrics.incrementReviveThrottles();
            return;
        }

        LOGGER.info("Reviving offers");
        Optional<SchedulerDriver> driver = Driver.getDriver();
        if (!driver.isPresent()) {
            throw new IllegalStateException("INTERNAL ERROR: No driver present for reviving offers.");
        }
        driver.get().reviveOffers();
        reviveRequested = false;

        // NOTE: We intentionally do not clear isSuppressed here. Instead, we wait until we've actually received new
        // offers. This is a 'just in case' measure to avoid a zombie state if the revive call is dropped. In practice,
        // there isn't a confirmed case of this ever happening, but it doesn't hurt to be conservative here.

        Metrics.incrementRevives();
    }
}
