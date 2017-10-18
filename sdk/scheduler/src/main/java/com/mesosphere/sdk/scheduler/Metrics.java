package com.mesosphere.sdk.scheduler;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OperationRecorder;
import org.apache.mesos.Protos;

/**
 * This class encapsulates the components necessary for tracking Scheduler metrics.
 */
public class Metrics {
    private static MetricRegistry metrics = new MetricRegistry();

    @VisibleForTesting
    static void reset() {
        metrics = new MetricRegistry();
    }

    public static MetricRegistry getRegistry() {
        return metrics;
    }

    // Offers
    private static final String RECEIVED_OFFERS = "offers.received";
    private static final String PROCESSED_OFFERS = "offers.processed";
    private static final String PROCESS_OFFERS = "offers.process";

    public static Counter getReceivedOffers() {
        return metrics.counter(RECEIVED_OFFERS);
    }

    public static Counter getProcessedOffers() {
        return metrics.counter(PROCESSED_OFFERS);
    }

    public static Timer getProcessOffersDuration() {
        return metrics.timer(PROCESS_OFFERS);
    }

    // Decline / Revive
    private static final String REVIVES = "revives";
    private static final String REVIVE_THROTTLES = "revives.throttles";
    private static final String DECLINE_SHORT = "declines.short";
    private static final String DECLINE_LONG = "declines.long";

    public static Counter getRevives() {
        return metrics.counter(REVIVES);
    }

    public static Counter getReviveThrottles() {
        return metrics.counter(REVIVE_THROTTLES);
    }

    public static Counter getDeclinesShort() {
        return metrics.counter(DECLINE_SHORT);
    }

    public static Counter getDeclinesLong() {
        return metrics.counter(DECLINE_LONG);
    }

    /**
     * This class records counter metrics for all Mesos Operations performed by the scheduler.
     */
    public static class OperationsCounter implements OperationRecorder {
        private static final OperationsCounter metricsRecorder = new OperationsCounter();
        private static final String PREFIX = "operation";

        private OperationsCounter() {
            // Do not instantiate this singleton class.
        }

        public static OperationsCounter getInstance() {
            return metricsRecorder;
        }

        @Override
        public void record(OfferRecommendation offerRecommendation) throws Exception {
            // Drop launch recommendations which are for bookkeeping purposes only
            if (offerRecommendation instanceof LaunchOfferRecommendation &&
                    !((LaunchOfferRecommendation) offerRecommendation).shouldLaunch()) {
                return;
            }

            // Metric name will be of the form "operation.launch"
            final String metricName = String.format(
                    "%s.%s",
                    PREFIX,
                    offerRecommendation.getOperation().getType().name().toLowerCase());

            metrics.counter(metricName).inc();
        }
    }

    // TaskStatuses
    public static void record(Protos.TaskStatus taskStatus) {
        final String prefix = "task_status";

        // Metric name will be of the form "task_status.running"
        final String metricName = String.format(
                "%s.%s",
                prefix,
                taskStatus.getState().name().toLowerCase());

        metrics.counter(metricName).inc();
    }
}
