package com.mesosphere.sdk.scheduler;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OperationRecorder;
import org.apache.mesos.Protos;

import java.util.HashMap;
import java.util.Map;

/**
 * This class encapsulates the components necessary for tracking Scheduler metrics.
 */
public class Metrics {
    private static final MetricRegistry metrics = new MetricRegistry();

    public static MetricRegistry getRegistry() {
        return metrics;
    }

    // Generated counters
    private static Map<String, Counter> counters = new HashMap<>();
    private static Counter getCounter(String name) {
        Counter counter = counters.get(name);
        if (counter == null) {
            counter = metrics.counter(name);
            counters.put(name, counter);
        }

        return counter;
    }

    // Offers
    private static final String RECEIVED_OFFERS = "offers.received";
    private static final String PROCESSED_OFFERS = "offers.processed";
    private static final String PROCESS_OFFERS = "offers.process";

    private static final Counter receivedOffers = metrics.counter(RECEIVED_OFFERS);
    private static final Counter processedOffers = metrics.counter(PROCESSED_OFFERS);
    private static final Timer processOffersDuration = metrics.timer(PROCESS_OFFERS);

    public static Counter getReceivedOffers() {
        return receivedOffers;
    }

    public static Counter getProcessedOffers() {
        return processedOffers;
    }

    public static Timer getProcessOffersDuration() {
        return processOffersDuration;
    }

    // Decline / Revive
    private static final String REVIVES = "revives";
    private static final String REVIVE_THROTTLES = "revives.throttles";
    private static final String DECLINE_SHORT = "declines.short";
    private static final String DECLINE_LONG = "declines.long";

    private static final Counter revives = metrics.counter(Metrics.REVIVES);
    private static final Counter reviveThrottles = metrics.counter(Metrics.REVIVE_THROTTLES);
    private static final Counter declinesShort = metrics.counter(DECLINE_SHORT);
    private static final Counter declinesLong = metrics.counter(DECLINE_LONG);

    public static Counter getRevives() {
        return revives;
    }

    public static Counter getReviveThrottles() {
        return reviveThrottles;
    }

    public static Counter getDeclinesShort() {
        return declinesShort;
    }

    public static Counter getDeclinesLong() {
        return declinesLong;
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

            getCounter(metricName).inc();
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

        getCounter(metricName).inc();
    }
}
