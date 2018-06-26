package com.mesosphere.sdk.framework;

import java.util.Optional;

import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.Metrics;
import com.mesosphere.sdk.scheduler.SchedulerConfig;

/**
 * Handles scheduling of Mesos suppress and revive calls.
 *
 * <ul>
 * <li>Suppress is performed whenever the underlying services are all idle and no further offers are needed. This allows
 * Mesos to scale to more frameworks. When the framework is suppressed, it will not receive any offers for any reason.
 * </li>
 * <li>From our perspective, a Revive call will reset any prior Suppress call, and/or reset any offers which were
 * previously declined, resulting in all offers being sent again. It should be invoked when any new work has appeared,
 * but Revive calls should be rate limited to avoid taxing Mesos.</li>
 * </ul>
 */
class ReviveManager {

    private static final Logger LOGGER = LoggingUtils.getLogger(ReviveManager.class);

    // Rate limiter for revive calls.
    private final TokenBucket reviveTokenBucket;
    // Whether suppress calls are enabled. We still 'simulate' suppress behavior internally, even when this is disabled.
    private final boolean suppressEnabled;

    // Whether we have had new work appear since the last time revive was called:
    private boolean reviveRequested;
    // Whether we think that we have suppressed offers from Mesos due to an idle state on our end:
    private boolean isSuppressed;

    ReviveManager(TokenBucket reviveTokenBucket, SchedulerConfig schedulerConfig) {
        this.reviveTokenBucket = reviveTokenBucket;
        this.suppressEnabled = schedulerConfig.isSuppressEnabled();
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
     *
     * Note that this also ensures that by flipping the {@code isSuppressed} flag if we receive an offer when we
     * SHOULD BE suppressed, we are ensuring that the future calls to {@code suppressIfActive} would reissue SUPPRESS.
     */
    synchronized void notifyOffersReceived() {
        isSuppressed = false;
        Metrics.notSuppressed();
    }

    /**
     * Issues a call to suppress offers, but only if the service does not already appear to be suppressed.
     * This should be invoked when the service(s) are all in an IDLE state, so that the offer stream may be temporarily
     * halted.
     */
    synchronized void suppressIfActive() {
        if (isSuppressed) {
            // Service doesn't need offers, but offers are already suppressed. Avoid duplicate suppress call.
            return;
        }

        // Service doesn't need offers, and offers are not suppressed. Suppress.
        if (suppressEnabled) {
            LOGGER.info("Suppressing offers");
            Optional<SchedulerDriver> driver = Driver.getDriver();
            if (!driver.isPresent()) {
                throw new IllegalStateException("INTERNAL ERROR: No driver present for suppressing offers");
            }
            driver.get().suppressOffers();
            Metrics.incrementSuppresses();
        } else {
            LOGGER.info("Refraining from suppressing offers (disabled via DISABLE_SUPPRESS)");
        }

        isSuppressed = true;
    }

    /**
     * Notifies the manager that a revive should be sent, but only if we're currently suppressed.
     * This should be invoked when the service(s) are in a WORKING state, so that any suppressed state gets cleared.
     */
    synchronized void requestReviveIfSuppressed() {
        if (!isSuppressed) {
            // Not suppressed, skip.
            return;
        }
        requestRevive();
    }

    /**
     * Notifies the manager that a revive should be sent soon. This is needed in either of the following cases:
     * <ul>
     * <li>The service has new work to do and any previously declined offers should be sent again</li>
     * <li>Offers are suppressed but the service is not idle (via {@link #requestReviveIfSuppressed()}</li>
     * </ul>
     */
    synchronized void requestRevive() {
        reviveRequested = true;
    }

    /**
     * Pings the manager to perform a revive call to Mesos if one was previously requested. This must be invoked
     * periodically to trigger revives. This structure allows us to enforce a rate limit on revive calls.
     */
    synchronized void reviveIfRequested() {
        if (!reviveRequested) {
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
